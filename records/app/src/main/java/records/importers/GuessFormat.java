package records.importers;

import annotation.qual.Value;
import annotation.units.TableDataRowIndex;
import com.google.common.base.Objects;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Label;
import log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.model.Paragraph;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyledText;
import records.data.CellPosition;
import records.data.Column;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.Table.Display;
import records.data.Table.InitialLoadDetails;
import records.data.TableId;
import records.data.TableManager;
import records.data.TextFileColumn.TextFileColumnListener;
import records.data.columntype.BlankColumnType;
import records.data.columntype.CleanDateColumnType;
import records.data.columntype.ColumnType;
import records.data.columntype.NumericColumnType;
import records.data.columntype.OrBlankColumnType;
import records.data.columntype.TextColumnType;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.DataDisplay;
import records.gui.grid.CellSelection;
import records.gui.stable.ColumnDetails;
import records.gui.stable.ColumnHandler;
import records.gui.stable.EditorKitCallback;
import records.gui.stf.TableDisplayUtility;
import records.importers.ChoicePoint.Choice;
import records.importers.ChoicePoint.ChoiceType;
import records.importers.ChoicePoint.Options;
import records.importers.ChoicePoint.Quality;
import records.importers.gui.ImportChoicesDialog;
import records.importers.gui.ImportChoicesDialog.SourceInfo;
import records.transformations.function.ToDate;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.Utility.IndexRange;
import utility.Workers;
import utility.Workers.Priority;
import utility.gui.TranslationUtility;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by neil on 20/10/2016.
 */
public class GuessFormat
{
    public static final int MAX_HEADER_ROWS = 5;
    public static final int INITIAL_ROWS_TEXT_FILE = 100;

    /**
     *
     * @param mgr
     * @param vals List of rows, where each row is list of values
     * @return
     */
    public static ChoicePoint<?, Format> guessGeneralFormat(UnitManager mgr, List<List<String>> vals)
    {
        // TODO guess some trim possibilities based on alphabets
        return ChoicePoint.<TrimChoice, Format>choose(Quality.PROMISING, 0, TrimChoice.getType(), (TrimChoice trim) -> {
            try
            {
                Format format = guessBodyFormat(mgr, vals.get(0).size(), trim, vals);
                // If they are all text record this as feasible but keep going in case we get better
                // result with more header rows:
                return ChoicePoint.success(
                        format.columnTypes.stream().allMatch(c -> c.type instanceof TextColumnType || c.type instanceof BlankColumnType) ? Quality.FALLBACK : Quality.PROMISING,
                        0.0,
                        format
                );
            }
            catch (GuessException e)
            {
                // Not a good guess then.
                return ChoicePoint.failure(e);
            }
        }, ImmutableList.of(new TrimChoice(0, 0, 0, 0)), s -> Either.left("GUI error"));
    }

    public static class GuessException extends UserException
    {
        public GuessException(String message)
        {
            super(message);
        }
    }

    public static class CharsetChoice extends Choice
    {
        private final Charset charset;
        private final String charsetName;

        public CharsetChoice(String charsetName)
        {
            this.charsetName = charsetName;
            charset = Charset.forName(charsetName);
        }

        public CharsetChoice(Charset charset)
        {
            this.charsetName = charset.displayName();
            this.charset = charset;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CharsetChoice that = (CharsetChoice) o;

            return charset.equals(that.charset);
        }

        @Override
        public int hashCode()
        {
            return charset.hashCode();
        }

        @Override
        public @Localized String toString()
        {
            return Utility.universal(charsetName);
        }

        public static ChoiceType<CharsetChoice> getType()
        {
            return new ChoiceType<>(CharsetChoice.class,"guess.charset", "guess-format/charset");
        }
    }

    // public for testing
    public static class TrimChoice extends Choice
    {
        public final int trimFromTop;
        public final int trimFromLeft;
        public final int trimFromRight;
        public final int trimFromBottom;

        // public for testing
        public TrimChoice(int trimFromTop, int trimFromBottom, int trimFromLeft, int trimFromRight)
        {
            this.trimFromTop = trimFromTop;
            this.trimFromLeft = trimFromLeft;
            this.trimFromRight = trimFromRight;
            this.trimFromBottom = trimFromBottom;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TrimChoice that = (TrimChoice) o;

            if (trimFromTop != that.trimFromTop) return false;
            if (trimFromLeft != that.trimFromLeft) return false;
            if (trimFromRight != that.trimFromRight) return false;
            return trimFromBottom == that.trimFromBottom;
        }

        @Override
        public int hashCode()
        {
            int result = trimFromTop;
            result = 31 * result + trimFromLeft;
            result = 31 * result + trimFromRight;
            result = 31 * result + trimFromBottom;
            return result;
        }

        @SuppressWarnings("i18n")
        @Override
        public @Localized String toString()
        {
            return "<trim, vert +" + trimFromTop + " -" + trimFromBottom + " horiz +" + trimFromLeft + " -" + trimFromRight + ">";
        }

        public static ChoiceType<TrimChoice> getType()
        {
            return new ChoiceType<>(TrimChoice.class, "guess.headerRow", "guess-format/headerRow");
        }

        public List<List<String>> trim(List<List<String>> original)
        {
            ArrayList<List<String>> trimmed = new ArrayList<>();
            for (int i = Math.max(0, trimFromTop); i < original.size() - Math.max(0, trimFromBottom); i++)
            {
                List<String> originalLine = original.get(i);
                int left = Utility.clampIncl(0, trimFromLeft, originalLine.size());
                int right = Utility.clampIncl(left, originalLine.size() - trimFromRight, originalLine.size());
                trimmed.add(originalLine.subList(left, right));
            }
            return trimmed;
        }
    }

    // public for testing
    public static class SeparatorChoice extends Choice
    {
        private final @Nullable String separator;

        // public for testing
        public SeparatorChoice(@Nullable String separator)
        {
            this.separator = separator;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            SeparatorChoice that = (SeparatorChoice) o;

            return Objects.equal(separator, that.separator);
        }

        @Override
        public int hashCode()
        {
            return separator == null ? 0 : separator.hashCode();
        }

        @Override
        public @Localized String toString()
        {
            if (separator == null)
                return TranslationUtility.getString("importer.sep.none");
            if (separator.equals(" "))
                return TranslationUtility.getString("importer.sep.space");
            if (separator.equals("\t"))
                return TranslationUtility.getString("importer.sep.tab");
            else
                return Utility.universal(separator);
        }

        public static ChoiceType<SeparatorChoice> getType()
        {
            return new ChoiceType<>(SeparatorChoice.class, "guess.separator", "guess-format/separator");
        }
    }

    public static class QuoteChoice extends Choice
    {
        private final @Nullable String quote;
        private final @Nullable String escapedQuote;

        public QuoteChoice(@Nullable String quote)
        {
            this.quote = quote;
            // Only option at the moment is doubled quote:
            this.escapedQuote = quote == null ? null : (quote + quote);
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            QuoteChoice that = (QuoteChoice) o;

            return quote != null ? quote.equals(that.quote) : that.quote == null;
        }

        @Override
        public int hashCode()
        {
            return quote != null ? quote.hashCode() : 0;
        }

        @Override
        public @Localized String toString()
        {
            return quote == null ? TranslationUtility.getString("importer.quote.none") : Utility.universal(quote);
        }

        public static ChoiceType<QuoteChoice> getType()
        {
            return new ChoiceType<>(QuoteChoice.class, "guess.quote", "guess-format/quote");
        }
    }

    // public for testing
    public static class ColumnCountChoice extends Choice
    {
        private final int columnCount;

        // public for testing
        public ColumnCountChoice(int columnCount)
        {
            this.columnCount = columnCount;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ColumnCountChoice that = (ColumnCountChoice) o;

            return columnCount == that.columnCount;
        }

        @Override
        public int hashCode()
        {
            return columnCount;
        }

        @Override
        public @Localized String toString()
        {
            return Integer.toString(columnCount);
        }

        public static ChoiceType<ColumnCountChoice> getType()
        {
            return new ChoiceType<>(ColumnCountChoice.class, "guess.columnCount", "guess-format/columnCount");
        }
    }

    private static SeparatorChoice sep(String separator)
    {
        return new SeparatorChoice(separator);
    }

    private static QuoteChoice quot(@Nullable String quoteChar)
    {
        return new QuoteChoice(quoteChar);
    }

    public static ChoicePoint<?, TextFormat> guessTextFormat(UnitManager mgr, Map<Charset, List<String>> initialByCharset)
    {
        return ChoicePoint.choose(Quality.PROMISING, 0, CharsetChoice.getType(), (CharsetChoice chc) ->
        {
            List<String> initialCheck = initialByCharset.get(chc.charset);
            if (initialCheck == null)
                throw new InternalException("initialByCharset key lookup returned null");

            @NonNull List<String> initial = initialCheck;

            List<TrimChoice> headerRowChoices = new ArrayList<>();
            // TODO guess based on alphabets

            return ChoicePoint.choose(headerRowChoices.isEmpty() ? Quality.NOTVIABLE : Quality.PROMISING, 0, TrimChoice.getType(), (TrimChoice trim) ->
                ChoicePoint.choose(Quality.PROMISING, 0, SeparatorChoice.getType(), (SeparatorChoice sep) ->
                ChoicePoint.choose(Quality.PROMISING, 0, QuoteChoice.getType(), (QuoteChoice quot) ->
                {
                    Multiset<Integer> counts = HashMultiset.create();
                    for (int i = 0; i < initial.size(); i++)
                    {
                        if (!initial.get(i).isEmpty())
                        {
                            counts.add(splitIntoColumns(initial.get(i), sep, quot).columnContents.size());
                        }
                    }

                    double score;
                    Quality quality;
                    if (counts.stream().allMatch(c -> c.intValue() == 0))
                    {
                        // None found; totally rubbish:
                        score = -Double.MAX_VALUE;
                        quality = Quality.FALLBACK;
                    } else
                    {
                        // Higher is better choice so negate:
                        score = -Utility.variance(counts);
                        quality = Quality.PROMISING;
                    }
                    List<ColumnCountChoice> viableColumnCounts = Multisets.copyHighestCountFirst(counts).entrySet().stream().limit(10).<@NonNull ColumnCountChoice>map(e -> new ColumnCountChoice(e.getElement())).collect(Collectors.<@NonNull ColumnCountChoice>toList());

                    return ChoicePoint.choose(quality, score, ColumnCountChoice.getType(), (ColumnCountChoice cc) ->
                    {
                        List<@NonNull List<@NonNull String>> initialVals = Utility.<@NonNull String, @NonNull List<@NonNull String>>mapList(initial, s -> splitIntoColumns(s, sep, quot).columnContents);
                        Format format = guessBodyFormat(mgr, cc.columnCount, trim, initialVals);
                        TextFormat textFormat = new TextFormat(format, sep.separator, quot.quote, chc.charset);
                        double proportionNonText = (double)textFormat.columnTypes.stream().filter(c -> !(c.type instanceof TextColumnType) && !(c.type instanceof BlankColumnType)).count() / (double)textFormat.columnTypes.size();
                        return ChoicePoint.<TextFormat>success(proportionNonText > 0 ? Quality.PROMISING : Quality.FALLBACK, proportionNonText, textFormat);
                    }, viableColumnCounts, null);
                }, Arrays.asList(quot(null), quot("\""), quot("\'")), enterSingleChar(QuoteChoice::new))
                , Arrays.asList(sep(";"), sep(","), sep("\t"), sep(":"), sep(" ")), enterSingleChar(SeparatorChoice::new))
                , headerRowChoices, s -> Either.left("GUI error"));
        }, initialByCharset.keySet().stream().<@NonNull CharsetChoice>map(CharsetChoice::new).collect(Collectors.<@NonNull CharsetChoice>toList()), GuessFormat::pickCharset);
    }

    private static Either<@Localized String, CharsetChoice> pickCharset(String s)
    {
        try
        {
            return Either.right(new CharsetChoice(Charset.forName(s)));
        }
        catch (Exception e)
        {
            return Either.left(TranslationUtility.getString("charset.not.available", s));
        }
    }

    private static <T> Function<String, Either<@Localized String, T>> enterSingleChar(Function<String, T> make)
    {
        return s -> {
            if (s.length() == 1)
                return Either.right(make.apply(s));
            else
                return Either.left(TranslationUtility.getString("error.single.char.only"));
        };
    }

    private static class RowInfo
    {
        // Each item is one column's content on this row
        private final List<String> columnContents = new ArrayList<>();
        // Each pair is (content, style)
        private final List<Pair<String, String>> originalContentAndStyle = new ArrayList<>();

    }

    // Split a row of text into columns, given a separator and a quote character
    private static RowInfo splitIntoColumns(String row, SeparatorChoice sep, QuoteChoice quot)
    {
        boolean inQuoted = false;
        StringBuilder sb = new StringBuilder();
        RowInfo r = new RowInfo();
        for (int i = 0; i < row.length();)
        {
            // First check for escaped quote (which may otherwise look like a quote):
            if (inQuoted && quot.escapedQuote != null && row.startsWith(quot.escapedQuote, i))
            {
                // Skip it:
                sb.append(quot.quote);
                i += quot.escapedQuote.length();

                if (quot.quote != null && quot.escapedQuote.endsWith(quot.quote))
                {
                    r.originalContentAndStyle.add(new Pair<>(quot.escapedQuote.substring(0, quot.escapedQuote.length() - quot.quote.length()), "escaped-quote-escape"));
                    r.originalContentAndStyle.add(new Pair<>(quot.quote, "escaped-quote-quote"));
                }
                else
                {
                    r.originalContentAndStyle.add(new Pair<>(quot.escapedQuote, "escaped-quote"));
                }
            }
            else if (quot.quote != null && row.startsWith(quot.quote, i) && (inQuoted || sb.toString().trim().isEmpty()))
            {
                if (!inQuoted)
                {
                    // Ignore the spaces beforehand:
                    sb = new StringBuilder();
                }
                inQuoted = !inQuoted;
                i += quot.quote.length();
                r.originalContentAndStyle.add(new Pair<>(quot.quote, inQuoted ? "quote-begin" : "quote-end"));
            }
            else if (!inQuoted && sep.separator != null && row.startsWith(sep.separator, i))
            {
                r.columnContents.add(sb.toString());
                r.originalContentAndStyle.add(new Pair<>(replaceTab(sep.separator), "separator"));
                sb = new StringBuilder();
                i += sep.separator.length();
            }
            else
            {
                // Nothing special:
                sb.append(row.charAt(i));
                r.originalContentAndStyle.add(new Pair<>(replaceTab(row.substring(i, i+1)), "normal"));
                i += 1;

            }
        }
        r.columnContents.add(sb.toString());
        return r;
    }

    private static String replaceTab(String s)
    {
        return s.replace("\t", "\u27FE");
    }

    // Note that the trim choice should not already have been applied
    private static Format guessBodyFormat(UnitManager mgr, int columnCount, TrimChoice trimChoice, @NonNull List<@NonNull List<@NonNull String>> untrimmed) throws GuessException
    {
        List<List<String>> initialVals = trimChoice.trim(untrimmed);
        // Per row, for how many columns is it viable to get column name?
        Map<Integer, Integer> viableColumnNameRows = new HashMap<>();
        List<ColumnType> columnTypes = new ArrayList<>();
        List<Integer> blankRows = new ArrayList<>();
        for (int columnIndex = 0; columnIndex < columnCount; columnIndex++)
        {
            // Have a guess at column columntype:
            boolean allNumeric = true;
            // The "blank", which may be empty string, or might be another value (e.g. "NA")
            String numericBlank = null;
            // Only false if we find content which is not parseable as a number:
            boolean allNumericOrBlank = true;
            boolean allBlank = true;
            List<DateFormat> possibleDateFormats = new ArrayList<>(ToDate.FORMATS.stream().<DateTimeFormatter>flatMap(l -> l.stream()).map(formatter -> new DateFormat(formatter, LocalDate::from)).collect(Collectors.<DateFormat>toList()));
            @Nullable String commonPrefix = null;
            @Nullable String commonSuffix = null;
            List<Integer> decimalPlaces = new ArrayList<>();
            for (int rowIndex = 0; rowIndex < initialVals.size(); rowIndex++)
            {
                List<String> row = initialVals.get(rowIndex);
                if (row.isEmpty() || row.stream().allMatch(String::isEmpty))
                {
                    // Whole row is blank
                    // Only add it once, not once per column:
                    if (columnIndex == 0)
                        blankRows.add(rowIndex);
                }
                else
                {
                    String val = columnIndex < row.size() ? row.get(columnIndex).trim() : "";
                    if (!val.isEmpty())
                    {
                        allBlank = false;

                        if (commonPrefix == null)
                        {
                            // Look for a prefix of currency symbol:
                            for (int i = 0; i < val.length(); i = val.offsetByCodePoints(i, 1))
                            {
                                if (Character.getType(val.codePointAt(i)) == Character.CURRENCY_SYMBOL)
                                {
                                    commonPrefix = val.substring(0, val.offsetByCodePoints(i, 1));
                                }
                                else
                                    break;
                            }
                        }
                        if (commonSuffix == null)
                        {
                            if (val.length() < 100)
                            {
                                int[] codepoints = val.codePoints().toArray();
                                // Look for a suffix of currency symbol:
                                for (int i = codepoints.length - 1; i >= 0 ; i--)
                                {
                                    if (codepoints[i] == '%' || Character.getType(codepoints[i]) == Character.CURRENCY_SYMBOL)
                                    {
                                        commonSuffix = new String(codepoints, i, codepoints.length - i);
                                    }
                                    else
                                        break;
                                }
                            }
                        }



                        int first;
                        // Not an else; if we just picked commonPrefix, we should find it here:
                        if (commonPrefix != null && val.startsWith(commonPrefix))
                        {
                            // Take off prefix and continue as is:
                            val = val.substring(commonPrefix.length()).trim();
                        }
                        else if (commonPrefix != null && !Character.isDigit(first = val.codePointAt(0)) && first != '+' && first != '-')
                        {
                            // We thought we had a prefix, but we haven't found it here, so give up:
                            commonPrefix = null;
                            allNumeric = false;
                            allNumericOrBlank = false;
                            //break;
                        }
                        
                        if (commonSuffix != null && val.endsWith(commonSuffix))
                        {
                            // Take off suffix and continue:
                            val = val.substring(0, val.length() - commonSuffix.length());
                        }
                        else if (commonSuffix != null && !Character.isDigit(val.length() - 1))
                        {
                            // We thought we had a prefix, but we haven't found it here, so give up:
                            commonSuffix = null;
                            allNumeric = false;
                            allNumericOrBlank = false;
                        }
                        
                        try
                        {
                            // TODO: support . as thousands separator and comma as decimal point
                            BigDecimal bd = new BigDecimal(val.replace(",", ""));
                            int dot = val.indexOf(".");
                            if (dot == -1)
                                decimalPlaces.add(0);
                            else
                                decimalPlaces.add(val.length() - (dot + 1));

                        }
                        catch (NumberFormatException e)
                        {
                            allNumeric = false;
                            if (numericBlank == null || numericBlank.equals(val))
                            {
                                // First non-number we've seen; this might be our blank:
                                numericBlank = val;
                            }
                            else
                            {
                                allNumericOrBlank = false;
                            }
                            commonPrefix = null;
                        }
                        // Minimum length for date is 6 by my count
                        if (val.length() < 6)
                            possibleDateFormats.clear();
                        else
                        {
                            String valPreprocessed = Utility.preprocessDate(val);
                            // Seems expensive but most will be knocked out immediately:
                            for (Iterator<DateFormat> dateFormatIt = possibleDateFormats.iterator(); dateFormatIt.hasNext(); )
                            {
                                try
                                {

                                    dateFormatIt.next().formatter.parse(valPreprocessed, LocalDate::from);
                                }
                                catch (DateTimeParseException e)
                                {
                                    dateFormatIt.remove();
                                }
                            }
                        }
                    }
                    else
                    {
                        // Found a blank:
                        allNumeric = false;
                    }
                }
            }
            int minDP = decimalPlaces.stream().mapToInt(i -> i).min().orElse(0);

            if (allBlank)
                columnTypes.add(ColumnType.BLANK);
            else if (!possibleDateFormats.isEmpty())
                columnTypes.add(new CleanDateColumnType(possibleDateFormats.get(0).formatter, possibleDateFormats.get(0).destQuery));
            else if (allNumeric)
            {
                columnTypes.add(new NumericColumnType(mgr.guessUnit(commonPrefix), minDP, commonPrefix, commonSuffix));
            }
            else if (allNumericOrBlank && numericBlank != null)
            {
                columnTypes.add(new OrBlankColumnType(new NumericColumnType(mgr.guessUnit(commonPrefix), minDP, commonPrefix, commonSuffix), numericBlank));
            }
            else
                columnTypes.add(new TextColumnType());
            // Go backwards to find column titles:

            /*
            for (int headerRow = headerRows - 1; headerRow >= 0; headerRow--)
            {
                // Must actually have our column in it:
                if (columnIndex < initialVals.get(headerRow).size() && !initialVals.get(headerRow).get(columnIndex).isEmpty())
                {
                    viableColumnNameRows.compute(headerRow, (a, pre) -> pre == null ? 1 : (1 + pre));
                }
            }
            */
        }
        int nonBlankColumnCount = (int)columnTypes.stream().filter(c -> !(c instanceof BlankColumnType)).count();
        // All must think it's viable, and then pick last one:
        Optional<List<String>> headerRow = viableColumnNameRows.entrySet().stream()
            //.filter(e -> e.getValue() == nonBlankColumnCount || e.getValue() == columnTypes.size())
            .max(Entry.comparingByKey()).map((Entry<@KeyFor("viableColumnNameRows") Integer, Integer> e) -> initialVals.get(e.getKey()));

        List<ColumnInfo> columns = new ArrayList<>(columnCount);
        HashSet<ColumnId> usedNames = new HashSet<>();
        for (int columnIndex = 0; columnIndex < columnTypes.size(); columnIndex++)
        {
            String original = headerRow.isPresent() && columnIndex < headerRow.get().size() ? headerRow.get().get(columnIndex) : "";
            StringBuilder stringBuilder = new StringBuilder();
            int[] codepoints = original.codePoints().toArray();
            boolean lastWasSpace = false;
            final int SPACE_CODEPOINT = 32;
            for (int i = 0; i < codepoints.length; i++)
            {
                int codepoint = codepoints[i];
                if (!ColumnId.validCharacter(codepoint, i == 0))
                {
                    // Can we make it valid with a prefix?
                    if (i == 0 && ColumnId.validCharacter(codepoint, false))
                    {
                        stringBuilder.append('C').append(new String(codepoints, i, 1));
                    }
                    // Otherwise invalid, so drop it.
                }
                else if (!lastWasSpace || codepoint != SPACE_CODEPOINT)
                {
                    lastWasSpace = codepoint == SPACE_CODEPOINT;
                    stringBuilder.append(new String(codepoints, i, 1));
                }
            }
            String validated = stringBuilder.toString().trim();
            if (validated.isEmpty())
                validated = "C";
            // Now check if it is taken:
            String prospectiveName = validated;
            int appendNum = 1;
            while (usedNames.contains(new ColumnId(prospectiveName)))
            {
                prospectiveName = validated + " " + appendNum;
                appendNum += 1;
            }
            ColumnId columnName = new ColumnId(prospectiveName);
            
            columns.add(new ColumnInfo(columnTypes.get(columnIndex), columnName));
            usedNames.add(columnName);
        }
        return new Format(trimChoice, columns);
    }

    public static class ImportInfo
    {
        private final TableId suggestedTableId; 
        //public final boolean linkFile;

        public ImportInfo(String suggestedName/*, boolean linkFile*/)
        {
            this.suggestedTableId = new TableId(suggestedName);
            //this.linkFile = linkFile;
        }
        
        public InitialLoadDetails getInitialLoadDetails(CellPosition destination)
        {
            return new InitialLoadDetails(suggestedTableId, destination, null);
        }
    }

    @OnThread(Tag.Simulation)
    public static void guessTextFormatGUI_Then(TableManager mgr, File file, String suggestedName, Map<Charset, List<String>> initial, Consumer<Pair<ImportInfo, TextFormat>> then)
    {
        ChoicePoint<?, TextFormat> choicePoints = guessTextFormat(mgr.getUnitManager(), initial);
        Platform.runLater(() ->
        {
            ColumnHandler columnHandler = new ColumnHandler()
            {
                @Override
                public @OnThread(Tag.FXPlatform) void modifiedDataItems(int startRowIncl, int endRowIncl)
                {
                }

                @Override
                public @OnThread(Tag.FXPlatform) void removedAddedRows(int startRowIncl, int removedRowsCount, int addedRowsCount)
                {
                }

                @Override
                public @OnThread(Tag.FXPlatform) void addedColumn(Column newColumn)
                {
                }

                @Override
                public @OnThread(Tag.FXPlatform) void removedColumn(ColumnId oldColumnId)
                {
                }

                @Override
                public void fetchValue(int rowIndex, FXPlatformConsumer<Boolean> focusListener, FXPlatformConsumer<CellPosition> relinquishFocus, EditorKitCallback setCellContent)
                {
                    // TODO
                }

                @Override
                public @OnThread(Tag.Simulation) @Value Object getValue(int index) throws InternalException, UserException
                {
                    //TODO
                    return DataTypeUtility.value("");
                }

                @Override
                public void columnResized(double width)
                {

                }

                @Override
                public boolean isEditable()
                {
                    return false;
                }
            };
            new ImportChoicesDialog<>(mgr, suggestedName, choicePoints, f -> null, choices -> {
                @Nullable CharsetChoice charsetChoice = choices.getChoice(CharsetChoice.getType());
                if (charsetChoice == null)
                    return null;
                else
                {
                    @SuppressWarnings("units")
                    @TableDataRowIndex int rows = Optional.ofNullable(initial.get(charsetChoice.charset)).map(l -> l.size()).orElse(0);
                    return new SourceInfo(ImmutableList.of(new ColumnDetails(new ColumnId("Text File Line"), DataType.TEXT, null, columnHandler)), rows);
                }
            }).showAndWait().ifPresent(then);
        });
        System.err.println(choicePoints);

    }

    // Finished flag indicates whether it finished or was not viable
    public static <C extends Choice, FORMAT extends Format> Choices findBestGuess(ChoicePoint<C, FORMAT> choicePoints)
    {
        Options<C> options = choicePoints.getOptions();
        if (options == null || options.isEmpty())
            return Choices.FINISHED;
        else
        {
            // At the moment, we just try first and if that gives promising all the way, we take it:
            for (C choice : options.quickPicks)
            {
                try
                {
                    ChoicePoint<?, FORMAT> next = choicePoints.select(choice);
                    if (next.getQuality() == Quality.PROMISING)
                    {
                        // Keep going!
                        Choices inner = findBestGuess(next);
                        if (inner.isFinished())
                        {
                            return inner.with(options.choiceType, choice);
                        }
                        // Otherwise try next choice
                    }
                }
                catch (InternalException e)
                {
                    // Go to next
                }
            }
            // No options found:
            return Choices.UNFINISHED;
        }
    }

    private static class GUI_Items implements ChangeListener<@Nullable CellSelection>
    {
        public final StyleClassedTextArea textArea;
        public final DataDisplay tableView;
        public final int numHeaderRows;
        // This structure needs to store data proportional to the size of the text file, so we
        // use a large flat array.  It's size is columns*rows*2, where
        // rowIndex*columns+columnIndex*2 pinpoints two integers: beginning and end of used range
        // we store rows together to plan for case where number of rows may not be known upfront.
        private int[] usedRanges;

        @OnThread(Tag.FXPlatform)
        @SuppressWarnings("initialization") // For passing this as change listener
        private GUI_Items(StyleClassedTextArea textArea, DataDisplay tableView, int numHeaderRows)
        {
            this.textArea = textArea;
            this.tableView = tableView;
            this.numHeaderRows = numHeaderRows;
            this.usedRanges = new int[100000];
            Arrays.fill(usedRanges, -1);
            // TODO
            //tableView.selectionProperty().addListener(this);
        }


        @OnThread(Tag.FXPlatform)
        public void setUsedRange(int columnIndex, int rowIndex, int start, int end)
        {
            int usedIndex = (rowIndex*(tableView.getDataDisplayBottomRightIncl().columnIndex - tableView.getDataDisplayTopLeftIncl().columnIndex) + columnIndex) * 2;
            // Poor man's array list:
            if (usedRanges.length <= usedIndex)
            {
                int oldLength = usedRanges.length;
                usedRanges = Arrays.copyOf(usedRanges, usedIndex + 2 + (usedRanges.length / 4));
                Arrays.fill(usedRanges, oldLength, usedRanges.length, -1);
            }
            usedRanges[usedIndex + 0] = start;
            usedRanges[usedIndex + 1] = end;
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public void changed(ObservableValue<? extends @Nullable CellSelection> prop, @Nullable CellSelection oldFocus, @Nullable CellSelection newFocus)
        {
            /* TODO
            if (oldFocus != null)
            {
                int usedIndex = (oldFocus.editPosition().rowIndex*tableView.getColumnCount() + oldFocus.editPosition().columnIndex) * 2;
                if (usedRanges[usedIndex] != -1)
                    overlayStyle(textArea, numHeaderRows, oldFocus.editPosition().rowIndex, new IndexRange(usedRanges[usedIndex], usedRanges[usedIndex+1]), "selected-cell", (a, b) -> Sets.difference(a, b));
            }
            if (newFocus != null)
            {
                int usedIndex = (newFocus.editPosition().rowIndex*tableView.getColumnCount() + newFocus.editPosition().columnIndex) * 2;
                if (usedRanges[usedIndex] != -1)
                    overlayStyle(textArea, numHeaderRows, newFocus.editPosition().rowIndex, new IndexRange(usedRanges[usedIndex], usedRanges[usedIndex+1]), "selected-cell", (a, b) -> Sets.union(a, b));
            }
            */
        }
    }

    @OnThread(Tag.FXPlatform)
    private static void previewFormat(TypeManager typeManager, File file, List<String> initialLines, TextFormat t, GUI_Items gui)
    {
        gui.textArea.replaceText(initialLines.stream().collect(Collectors.joining("\n")));
        gui.tableView.setColumns(ImmutableList.of(), null, null);
        TextAreaFiller textAreaFiller = new TextAreaFiller(gui);

        gui.textArea.setParagraphGraphicFactory(sourceLine -> {
            Label label = new Label(Integer.toString(sourceLine));
            label.getStyleClass().add("line-number");
            return label;
        });

        Workers.onWorkerThread("Loading" + file.getName(), Priority.LOAD_FROM_DISK, () -> {
            try
            {
                @OnThread(Tag.Simulation) RecordSet recordSet = TextImporter.makeRecordSet(typeManager, file, t, textAreaFiller);
                Platform.runLater(() -> {
                    gui.tableView.setColumns(TableDisplayUtility.makeStableViewColumns(recordSet, new Pair<>(Display.ALL, c -> true), c -> null, (r, c) -> CellPosition.ORIGIN, null), null, null);
                });
            }
            catch (IOException | InternalException | UserException e)
            {
                Log.log(e);
                Platform.runLater(() -> {
                    gui.tableView.setColumns(ImmutableList.of(), null, null);
                    //gui.tableView.setMessageWhenEmpty(new MessageWhenEmpty(e.getLocalizedMessage()));
                });

            }
        });

        // TODO put preview back by adding listeners to import process
        /*
        for (int row = t.headerRows; row < initial.size(); row++)
        {
            if (t.separator == null)
            {
                // TODO: is quoting still valid if there's only one column?
                tableView.getItems().add(Collections.singletonList(initial.get(row)));
                textArea.appendText(initial.get(row));
            }
            else
            {
                RowInfo split = splitIntoColumns(initial.get(row), new SeparatorChoice(t.separator), new QuoteChoice(t.quote));
                tableView.getItems().add(split.columnContents);
                ReadOnlyStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> doc = ReadOnlyStyledDocument.fromString("", Collections.<@NonNull String>emptyList(), Collections.<@NonNull String>emptyList(), StyledText.<Collection<@NonNull String>>textOps());
                for (int i = 0; i < split.originalContentAndStyle.size(); i++)
                {
                    doc = doc.concat(ReadOnlyStyledDocument.fromString(split.originalContentAndStyle.get(i).getFirst(), Collections.emptyList(), Collections.singletonList(split.originalContentAndStyle.get(i).getSecond()), StyledText.<Collection<@NonNull String>>textOps()));
                }
                textArea.append(doc);
                textArea.appendText("\n");
            }
        }*/
    }

    private static class TextAreaFiller implements TextFileColumnListener
    {
        private final GUI_Items gui;

        public TextAreaFiller(GUI_Items guiItems)
        {
            this.gui = guiItems;
        }

        @Override
        @OnThread(Tag.Simulation)
        public void usedLine(int rowIndex, int columnIndex, String line, IndexRange usedPortion)
        {
            Platform.runLater(() ->
            {
                while (gui.textArea.getParagraphs().size() <= rowIndex + gui.numHeaderRows)
                    gui.textArea.appendText("\n");
                Paragraph<Collection<String>, StyledText<Collection<String>>, Collection<String>> para = gui.textArea.getParagraphs().get(rowIndex + gui.numHeaderRows);
                if (para.getText().isEmpty())
                {
                    int pos = gui.textArea.getDocument().getAbsolutePosition(rowIndex + gui.numHeaderRows, 0);
                    gui.textArea.replaceText(pos, pos, replaceTab(line));
                }
                overlayStyle(gui.textArea, gui.numHeaderRows, rowIndex, usedPortion, "used", (a, b) -> Sets.union(a, b));
                gui.setUsedRange(columnIndex, rowIndex, usedPortion.start, usedPortion.end);
            });
        }
    }


    @OnThread(Tag.FXPlatform)
    private static void overlayStyle(StyleClassedTextArea textArea, int numHeaderRows, int rowIndex, IndexRange usedPortion, String style, BiFunction<Set<String>, Set<String>, SetView<String>> setOp)
    {
        textArea.setStyleSpans(numHeaderRows + rowIndex, 0, textArea.getStyleSpans(numHeaderRows + rowIndex).overlay(
            StyleSpans.<Collection<String>>singleton(Collections.emptyList(), usedPortion.start).concat(StyleSpans.<Collection<String>>singleton(Collections.singletonList(style), usedPortion.getLength())),
            (a, b) -> setOp.apply(new HashSet<String>(a), new HashSet<String>(b)).immutableCopy())
        );
    }
}
