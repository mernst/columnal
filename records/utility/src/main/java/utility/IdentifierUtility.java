package utility;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.identifier.qual.UnitIdentifier;
import annotation.units.RawInputLocation;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CodePointCharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.grammar.ExpressionLexer;
import records.grammar.ExpressionParser.IdentContext;
import records.grammar.UnitLexer;
import records.grammar.UnitParser.SingleUnitContext;
import utility.Utility.DescriptiveErrorListener;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

public class IdentifierUtility
{
    @SuppressWarnings("identifier")
    public static @Nullable @UnitIdentifier String asUnitIdentifier(String src)
    {
        if (Utility.lexesAs(src, UnitLexer::new, UnitLexer.IDENT))
            return src;
        else
            return null;
    }

    @SuppressWarnings("identifier")
    public static @Nullable @ExpressionIdentifier String asExpressionIdentifier(String src)
    {
        if (Utility.lexesAs(src, ExpressionLexer::new, ExpressionLexer.IDENT))
            return src;
        else
            return null;
    }

    @SuppressWarnings("identifier")
    public static @ExpressionIdentifier String fromParsed(records.grammar.ExpressionParser.IdentContext parsedIdent)
    {
        return parsedIdent.getText();
    }

    @SuppressWarnings("identifier")
    public static @ExpressionIdentifier String fromParsed(records.grammar.FormatParser.IdentContext parsedIdent)
    {
        return parsedIdent.getText();
    }

    @SuppressWarnings("identifier")
    public static @ExpressionIdentifier String fromParsed(records.grammar.FormatParser.ColumnNameContext parsedIdent)
    {
        return parsedIdent.getText();
    }

    public static @Nullable @ExpressionIdentifier String fromParsed(records.grammar.DataParser.LabelContext parsedIdent)
    {
        return asExpressionIdentifier(parsedIdent.labelName().getText());
    }

    @SuppressWarnings("identifier")
    public static @UnitIdentifier String fromParsed(SingleUnitContext parsedIdent)
    {
        return parsedIdent.IDENT().getText();
    }
    
    public static class Consumed<T>
    {
        public final T item;
        public final @RawInputLocation int positionAfter;
        public final ImmutableSet<@RawInputLocation Integer> removedCharacters;

        public Consumed(T item, @RawInputLocation int positionAfter, ImmutableSet<@RawInputLocation Integer> removedCharacters)
        {
            this.item = item;
            this.positionAfter = positionAfter;
            this.removedCharacters = removedCharacters;
        }
    }
    
    
    public static @Nullable Consumed<@ExpressionIdentifier String> consumeExpressionIdentifier(String content, @RawInputLocation int startFrom, @RawInputLocation int caretPos)
    {
        CodePointCharStream inputStream = CharStreams.fromString(content.substring(startFrom));
        Lexer lexer = new ExpressionLexer(inputStream);
        DescriptiveErrorListener errorListener = new DescriptiveErrorListener();
        lexer.addErrorListener(errorListener);
        Token token = lexer.nextToken();
        // If there any errors, abort:
        if (!errorListener.errors.isEmpty())
            return null;
        else if (token.getType() == ExpressionLexer.IDENT)
        {
            @SuppressWarnings("units")
            @RawInputLocation int end = startFrom + token.getStopIndex() + 1;

            // Find all the consecutive spaces:
            @RawInputLocation int posAfterLastSpace = end;
            while (posAfterLastSpace < content.length() && content.charAt(posAfterLastSpace) == ' ')
            {
                posAfterLastSpace += RawInputLocation.ONE;
            }
            
            // Is it followed by at least one space?
            if (posAfterLastSpace > end)
            {
                // Here's the options:
                // 1: there are one or more spaces, the caret is somewhere in them, preserve one before (if present), and one after (if present), delete rest.
                //    If followed by identifier, glue together
                // 2: there are one or more spaces, the caret doesn't touch any of them, and
                //   (a) there is an identifier following: delete down to one space and glue
                //   (b) there is not an identifier following: delete all
                
                @Nullable Consumed<@ExpressionIdentifier String> identAfter = consumeExpressionIdentifier(content, posAfterLastSpace, caretPos);
                
                // Is the caret in there?
                if (end <= caretPos && caretPos <= posAfterLastSpace)
                {
                    // It is -- preserve at most one space before and one after:
                    Set<@RawInputLocation Integer> removed = new HashSet<>();
                    for (@RawInputLocation int i = end + RawInputLocation.ONE; i < caretPos; i++)
                    {
                        removed.add(i);
                    }
                    for (@RawInputLocation int i = caretPos + RawInputLocation.ONE; i < posAfterLastSpace; i++)
                    {
                        removed.add(i);
                    }
                    String spaces = (end < caretPos ? " " : "") + (caretPos < posAfterLastSpace ? " " : "");
                    if (identAfter != null)
                    {
                        @SuppressWarnings("identifier")
                        @ExpressionIdentifier String withSpaces = token.getText() + spaces + identAfter.item;
                        return new Consumed<@ExpressionIdentifier String>(withSpaces, identAfter.positionAfter, ImmutableSet.<@RawInputLocation Integer>copyOf(Sets.<@RawInputLocation Integer>union(removed, identAfter.removedCharacters)));
                    }
                    else
                    {
                        @SuppressWarnings("identifier")
                        @ExpressionIdentifier String withSpaces = token.getText() + spaces;
                        return new Consumed<@ExpressionIdentifier String>(withSpaces, posAfterLastSpace, ImmutableSet.copyOf(removed));
                    }
                }
                else
                {
                    // Caret not in there:
                    Set<@RawInputLocation Integer> removed = new HashSet<>();
                    for (@RawInputLocation int i = end + (identAfter != null ? RawInputLocation.ONE : RawInputLocation.ZERO); i < posAfterLastSpace; i++)
                    {
                        removed.add(i);
                    }
                    if (identAfter != null)
                    {
                        @SuppressWarnings("identifier")
                        @ExpressionIdentifier String glued = token.getText() + " " + identAfter.item;
                        return new Consumed<@ExpressionIdentifier String>(glued, identAfter.positionAfter, ImmutableSet.<@RawInputLocation Integer>copyOf(Sets.<@RawInputLocation Integer>union(removed, identAfter.removedCharacters)));
                    }
                    else
                    {
                        @SuppressWarnings("identifier")
                        @ExpressionIdentifier String ident = token.getText();
                        return new Consumed<>(ident, posAfterLastSpace, ImmutableSet.copyOf(removed));
                    }
                }
            }
            @SuppressWarnings("identifier")
            @ExpressionIdentifier String tokenIdent = token.getText();
            return new Consumed<>(tokenIdent, end, ImmutableSet.of());
        }
        else
            return null;
    }

    public static @Nullable Consumed<String> consumePossiblyScopedExpressionIdentifier(String content, @RawInputLocation int startFrom, @RawInputLocation int includeTrailingSpaceOrDoubleSpaceIfEndsAt)
    {
        @Nullable Consumed<@ExpressionIdentifier String> before = consumeExpressionIdentifier(content, startFrom, includeTrailingSpaceOrDoubleSpaceIfEndsAt);
        if (before != null)
        {
            if (before.positionAfter < content.length() && content.charAt(before.positionAfter) == '\\')
            {
                @Nullable Consumed<@ExpressionIdentifier String> after = consumeExpressionIdentifier(content, before.positionAfter + RawInputLocation.ONE, includeTrailingSpaceOrDoubleSpaceIfEndsAt);
                if (after != null)
                {
                    return new Consumed<>(before.item + "\\" + after.item, after.positionAfter, ImmutableSet.<@RawInputLocation Integer>copyOf(Sets.<@RawInputLocation Integer>union(before.removedCharacters, after.removedCharacters)));
                }
            }
            return new Consumed<>(before.item, before.positionAfter, before.removedCharacters);
        }
        return null;
    }

    @SuppressWarnings({"identifier", "units"})
    public static @Nullable Pair<@UnitIdentifier String, @RawInputLocation Integer> consumeUnitIdentifier(String content, int startFrom)
    {
        CodePointCharStream inputStream = CharStreams.fromString(content.substring(startFrom));
        Lexer lexer = new UnitLexer(inputStream);
        DescriptiveErrorListener errorListener = new DescriptiveErrorListener();
        lexer.addErrorListener(errorListener);
        Token token = lexer.nextToken();
        // If there any errors, abort:
        if (!errorListener.errors.isEmpty())
            return null;
        else if (token.getType() == UnitLexer.IDENT)
            return new Pair<>(token.getText(), startFrom + token.getStopIndex() + 1);
        else
            return null;
    }
    
    private static enum Last {START, SPACE, VALID}
    
    // Finds closest valid expression identifier
    @SuppressWarnings("identifier")
    public static @ExpressionIdentifier String fixExpressionIdentifier(String original, @ExpressionIdentifier String hint)
    {
        StringBuilder processed = new StringBuilder();
        Last last = Last.START;
        for (int c : original.codePoints().toArray())
        {
            if (last == Last.START && Character.isAlphabetic(c))
            {
                processed.appendCodePoint(c);
                last = Last.VALID;
            }
            else if (last == Last.START && Character.isDigit(c))
            {
                // Stick an N on the front:
                processed.append('N').appendCodePoint(c);
                last = Last.VALID;
            }
            else if (last != Last.START && (Character.isAlphabetic(c) || Character.getType(c) == Character.OTHER_LETTER || Character.isDigit(c)))
            {
                processed.appendCodePoint(c);
                last = Last.VALID;
            }
            else if (last != Last.SPACE && (c == '_' || Character.isWhitespace(c)))
            {
                processed.appendCodePoint(c == '_' ? c : ' ');
                last = Last.SPACE;
            }
            // Swap others to space:
            else if (last != Last.SPACE)
            {
                processed.appendCodePoint(' ');
                last = Last.SPACE;
            }
        }
        
        @ExpressionIdentifier String r = asExpressionIdentifier(processed.toString().trim());
        if (r != null)
            return r;
        else
            return hint;
    }
    
    public static @ExpressionIdentifier String identNum(@ExpressionIdentifier String stem, int number)
    {
        return fixExpressionIdentifier(stem + " " + number, stem);
    }
}
