package records.gui;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.model.ReadOnlyStyledDocument;
import org.fxmisc.richtext.model.StyledDocument;
import org.fxmisc.richtext.model.StyledText;
import org.fxmisc.wellbehaved.event.EventPattern;
import org.fxmisc.wellbehaved.event.InputMap;
import org.fxmisc.wellbehaved.event.Nodes;
import records.data.Column;
import records.data.Column.ProgressListener;
import records.data.RecordSet;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DataTypeVisitorEx;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue.DataTypeVisitorGetEx;
import records.data.datatype.NumberInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeValue;
import records.data.datatype.DataTypeValue.GetValue;
import records.data.datatype.TypeId;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.gui.stf.*;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformFunctionInt;
import utility.FXPlatformFunctionIntUser;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.TaggedValue;
import utility.Utility;
import records.gui.stable.StableView.ColumnHandler;
import records.gui.stable.StableView.CellContentReceiver;
import utility.Utility.ListEx;
import utility.Utility.ListExList;
import utility.Workers;
import utility.gui.FXUtility;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Created by neil on 01/05/2017.
 */
@OnThread(Tag.FXPlatform)
public class TableDisplayUtility
{

    public static List<Pair<String, ColumnHandler>> makeStableViewColumns(RecordSet recordSet)
    {
        return Utility.mapList(recordSet.getColumns(), col -> {
            try
            {
                return getDisplay(col);
            }
            catch (InternalException | UserException e)
            {
                // Show a dummy column with an error message:
                return new Pair<>(col.getName().getRaw(), new ColumnHandler()
                {
                    @Override
                    public void fetchValue(int rowIndex, CellContentReceiver receiver, int firstVisibleRowIndexIncl, int lastVisibleRowIndexIncl)
                    {
                        receiver.setCellContent(rowIndex, new Label("Error: " + e.getLocalizedMessage()));
                    }

                    @Override
                    public void columnResized(double width)
                    {

                    }

                    @Override
                    public @Nullable InputMap<?> getInputMapForParent(int rowIndex)
                    {
                        return null;
                    }

                    @Override
                    public void edit(int rowIndex, @Nullable Point2D scenePoint, FXPlatformRunnable endEdit)
                    {
                        Utility.logStackTrace("Called edit when not editable");
                    }

                    @Override
                    public boolean isEditable()
                    {
                        return false;
                    }

                    @Override
                    public boolean editHasFocus(int rowIndex)
                    {
                        return false;
                    }
                });
            }
        });
    }

    private static Pair<String, ColumnHandler> getDisplay(@NonNull Column column) throws UserException, InternalException
    {
        return new Pair<>(column.getName().getRaw(), column.getType().<ColumnHandler, UserException>applyGet(new DataTypeVisitorGetEx<ColumnHandler, UserException>()
        {
            @Override
            @OnThread(Tag.FXPlatform)
            public ColumnHandler number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException, UserException
            {
                return NumberDisplay.makeDisplayCache(g, displayInfo.getDisplayInfo(), column);
            }

            @Override
            public ColumnHandler text(GetValue<@Value String> g) throws InternalException, UserException
            {
                class StringDisplay extends StackPane
                {
                    private final StyleClassedTextArea textArea;
                    private final BooleanBinding notFocused;
                    private @Nullable FXPlatformRunnable endEdit;

                    @OnThread(Tag.FXPlatform)
                    public StringDisplay(int rowIndex, String originalValue)
                    {
                        Label beginQuote = new Label("\u201C");
                        Label endQuote = new Label("\u201D");
                        beginQuote.getStyleClass().add("string-display-quote");
                        endQuote.getStyleClass().add("string-display-quote");
                        StackPane.setAlignment(beginQuote, Pos.TOP_LEFT);
                        StackPane.setAlignment(endQuote, Pos.TOP_RIGHT);
                        //StackPane.setMargin(beginQuote, new Insets(0, 0, 0, 3));
                        //StackPane.setMargin(endQuote, new Insets(0, 3, 0, 0));
                        textArea = new StyleClassedTextArea(true);
                        textArea.getStyleClass().add("string-display");
                        textArea.replaceText(originalValue);
                        textArea.setWrapText(false);
                        getChildren().addAll(beginQuote, textArea); //endQuote, textArea);
                        // TODO allow editing, and call column.modified when it happens
                        Nodes.addInputMap(textArea, InputMap.consume(EventPattern.keyPressed(KeyCode.ENTER), e -> {
                            if (endEdit != null)
                                endEdit.run();
                            e.consume();
                        }));
                        FXUtility.addChangeListenerPlatformNN(textArea.focusedProperty(), focused ->
                        {
                            if (!focused)
                            {
                                String value = textArea.getText();
                                Workers.onWorkerThread("Storing value " + value, Workers.Priority.SAVE_ENTRY, () -> Utility.alertOnError_(() -> {
                                        g.set(rowIndex, DataTypeUtility.value(value));
                                        column.modified(rowIndex);
                                }));
                                textArea.deselect();
                            }
                        });
                        // Doesn't get mouse events unless focused:
                        notFocused = textArea.focusedProperty().not();
                        textArea.mouseTransparentProperty().bind(notFocused);
                    }

                    public void edit(boolean selectAll, FXPlatformRunnable endEdit)
                    {
                        textArea.requestFocus();
                        if (selectAll)
                            textArea.selectAll();
                        this.endEdit = endEdit;
                    }
                }

                return new DisplayCache<@Value String, StringDisplay>(g, null, s -> s) {
                    @Override
                    protected StringDisplay makeGraphical(int rowIndex, @Value String value)
                    {
                        return new StringDisplay(rowIndex, value);
                    }

                    @Override
                    public void edit(int rowIndex, @Nullable Point2D scenePoint, FXPlatformRunnable endEdit)
                    {
                        @Nullable StringDisplay display = getRowIfShowing(rowIndex);
                        if (display != null)
                        {
                            display.edit(scenePoint == null, endEdit);
                        }
                    }

                    @Override
                    public @Nullable InputMap<?> getInputMapForParent(int rowIndex)
                    {
                        return null; // TODO allow typing to start editing
                    }

                    @Override
                    public boolean isEditable()
                    {
                        return true;
                    }

                    @Override
                    public boolean editHasFocus(int rowIndex)
                    {
                        @Nullable StringDisplay display = getRowIfShowing(rowIndex);
                        if (display != null)
                            return display.isFocused();
                        return false;
                    }
                };
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public ColumnHandler bool(GetValue<@Value Boolean> g) throws InternalException, UserException
            {
                return makeField(column.getType());
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public ColumnHandler date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException, UserException
            {
                return makeField(column.getType());
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public ColumnHandler tagged(TypeId typeName, ImmutableList<TagType<DataTypeValue>> tagTypes, GetValue<Integer> g) throws InternalException, UserException
            {
                return makeField(column.getType());
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public ColumnHandler tuple(ImmutableList<DataTypeValue> types) throws InternalException, UserException
            {
                return makeField(column.getType());
            }

            @Override
            public ColumnHandler array(@Nullable DataType inner, GetValue<Pair<Integer, DataTypeValue>> g) throws InternalException, UserException
            {
                throw new UnimplementedException();
            }
        }));
    }

    // package-visible
    static StyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> docFromSegments(StyledText<Collection<String>>... segments)
    {
        ReadOnlyStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> doc = ReadOnlyStyledDocument.<Collection<String>, StyledText<Collection<String>>, Collection<String>>fromSegment((StyledText<Collection<String>>)segments[0], Collections.emptyList(), Collections.emptyList(), StyledText.<Collection<String>>textOps());
        for (int i = 1; i < segments.length; i++)
        {
            doc = doc.concat(ReadOnlyStyledDocument.<Collection<String>, StyledText<Collection<String>>, Collection<String>>fromSegment(segments[i], Collections.emptyList(), Collections.emptyList(), StyledText.<Collection<String>>textOps()));
        }
        return doc;
    }

    public static interface ComponentMaker<T>
    {
        @OnThread(Tag.FXPlatform)
        public Component<? extends T> makeComponent(ImmutableList<Component<?>> parents, T value) throws InternalException, UserException;
    }

    public static class GetValueAndComponent<T>
    {
        public final GetValue<T> g;
        public final ComponentMaker<T> makeComponent;

        public GetValueAndComponent(GetValue<T> g, ComponentMaker<T> makeComponent)
        {
            this.g = g;
            this.makeComponent = makeComponent;
        }

        public DisplayCacheSTF<T> makeDisplayCache()
        {
            return new DisplayCacheSTF<>(g, value -> new StructuredTextField<>(makeComponent.makeComponent(ImmutableList.of(), value)));
        }
    }

    // public for testing:
    @OnThread(Tag.FXPlatform)
    public static DisplayCacheSTF<?> makeField(DataTypeValue dataTypeValue) throws InternalException
    {
        return valueAndComponent(dataTypeValue).makeDisplayCache();
    }

    @OnThread(Tag.FXPlatform)
    private static GetValueAndComponent<?> valueAndComponent(DataTypeValue dataTypeValue) throws InternalException
    {
        return dataTypeValue.applyGet(new DataTypeVisitorGetEx<GetValueAndComponent<?>, InternalException>()
        {
            @Override
            public GetValueAndComponent<?> number(GetValue<Number> g, NumberInfo displayInfo) throws InternalException
            {
                return new GetValueAndComponent<>(g, NumberEntry::new);
            }

            @Override
            public GetValueAndComponent<?> text(GetValue<String> g) throws InternalException
            {
                return new GetValueAndComponent<>(g, TextEntry::new);
            }

            @Override
            public GetValueAndComponent<?> bool(GetValue<Boolean> g) throws InternalException
            {
                return new GetValueAndComponent<>(g, BoolComponent::new);
            }

            @Override
            public GetValueAndComponent<?> date(DateTimeInfo dateTimeInfo, GetValue<TemporalAccessor> g) throws InternalException
            {
                switch (dateTimeInfo.getType())
                {
                    case YEARMONTHDAY:
                        return new GetValueAndComponent<>(g, YMD::new);
                    case YEARMONTH:
                        return new GetValueAndComponent<>(g, YM::new);
                    case TIMEOFDAY:
                        return new GetValueAndComponent<>(g, TimeComponent::new);
                    case TIMEOFDAYZONED:
                        return new GetValueAndComponent<>(g, (parents, value) -> new Component2<OffsetTime, LocalTime, ZoneOffset>(parents, subParents -> new TimeComponent(subParents, value), null, subParents -> new PlusMinusOffsetComponent(parents, value.get(ChronoField.OFFSET_SECONDS)), OffsetTime::of));
                    case DATETIME:
                        return new GetValueAndComponent<>(g, (parents, value) -> new Component2<LocalDateTime, LocalDate, LocalTime>(parents, subParents -> new YMD(subParents, value), " ", subParents -> new TimeComponent(subParents, value), LocalDateTime::of));
                    case DATETIMEZONED:
                        return new GetValueAndComponent<>(g, (parents0, value) ->
                            new Component2<ZonedDateTime, LocalDateTime, ZoneId>(parents0,
                                parents1 -> new Component2<LocalDateTime, LocalDate, LocalTime>(
                                        parents1, parents2 -> new YMD(parents2, value), " ", parents2 -> new TimeComponent(parents2, value), LocalDateTime::of),
                                " ",
                                parents1 -> new ZoneIdComponent(parents1, ((ZonedDateTime)value).getZone()), ZonedDateTime::of)
                        );
                }
                throw new InternalException("Unknown type: " + dateTimeInfo.getType());
            }

            @Override
            public GetValueAndComponent<?> tagged(TypeId typeName, ImmutableList<TagType<DataTypeValue>> tagTypes, GetValue<Integer> g) throws InternalException
            {
                GetValue<TaggedValue> getTagged = DataTypeUtility.toTagged(g, tagTypes);
                return new GetValueAndComponent<TaggedValue>(getTagged, (parents, v) -> new TaggedComponent(parents, tagTypes, v));
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public GetValueAndComponent<?> tuple(ImmutableList<DataTypeValue> types) throws InternalException
            {
                List<GetValueAndComponent<?>> gvacs = new ArrayList<>(types.size());
                for (DataTypeValue type : types)
                {
                    gvacs.add(valueAndComponent(type));
                }
                GetValue<Object[]> tupleGet = new GetValue<Object[]>()
                {
                    @Override
                    public Object @OnThread(Tag.Simulation) @NonNull [] getWithProgress(int index, @Nullable ProgressListener progressListener) throws UserException, InternalException
                    {
                        Object[] r = new Object[types.size()];
                        for (int i = 0; i < r.length; i++)
                        {
                            r[i] = gvacs.get(i).g.getWithProgress(index, progressListener);
                        }
                        return r;
                    }

                    @Override
                    public @OnThread(Tag.Simulation) void set(int index, Object[] value) throws InternalException, UserException
                    {
                        for (int i = 0; i < value.length; i++)
                        {
                            // Cast because we can't express type safety:
                            ((GetValue)gvacs.get(i).g).set(index, value[i]);
                        }
                    }
                };


                return new GetValueAndComponent<Object[]>(tupleGet, (parents, value) -> {
                    ArrayList<FXPlatformFunctionIntUser<ImmutableList<Component<?>>, Component<? extends Object>>> components = new ArrayList<>(types.size());
                    for (int i = 0; i < types.size(); i++)
                    {
                        GetValueAndComponent<?> gvac = gvacs.get(i);
                        // Have to use some casts because we can't express the type safety:
                        ComponentMaker<Object> makeComponent = (ComponentMaker)gvac.makeComponent;
                        int iFinal = i;
                        components.add(subParents -> makeComponent.makeComponent(subParents, value[iFinal]));
                    }
                    return new FixedLengthComponentList<Object[], Object>(parents, "(", components, ",", ")", List::toArray);
                });
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public GetValueAndComponent<?> array(@Nullable DataType inner, GetValue<Pair<Integer, DataTypeValue>> g) throws InternalException
            {
                if (inner == null)
                    throw new InternalException("Can't make components for the empty list type");

                @NonNull DataType innerType = inner;

                return new GetValueAndComponent<ListEx>(DataTypeUtility.toListEx(innerType, g), (parents, value) ->
                {
                    List<@Value Object> fxList = DataTypeUtility.fetchList(value);

                    // Have to use some casts because we can't express the type safety:
                    ComponentMaker<Object> makeComponent = (ComponentMaker)valueAndComponent(innerType.fromCollapsed((index, prog) -> 0)).makeComponent;
                    List<FXPlatformFunctionIntUser<ImmutableList<Component<?>>, Component<? extends @NonNull Object>>> components = new ArrayList<>(fxList.size());
                    for (int i = 0; i < fxList.size(); i++)
                    {
                        int iFinal = i;
                        components.add(subParents -> makeComponent.makeComponent(subParents, fxList.get(iFinal)));
                    }

                    return new VariableLengthComponentList<ListEx, Object>(parents, "[", ",", components, "]", ListExList::new)
                    {
                        @Override
                        protected Component<? extends Object> makeNewEntry(ImmutableList<Component<?>> subParents) throws InternalException
                        {
                            return component(subParents, innerType);
                        }
                    };
                });
            }
        });
    }

    @OnThread(Tag.FXPlatform)
    public static Component<@NonNull ?> component(ImmutableList<Component<?>> parents, DataType dataType) throws InternalException
    {
        return dataType.apply(new DataTypeVisitorEx<Component<@NonNull ?>, InternalException>()
        {
            @Override
            @OnThread(Tag.FXPlatform)
            public Component<@NonNull ?> number(NumberInfo displayInfo) throws InternalException
            {
                return new NumberEntry(parents, null);
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public Component<@NonNull ?> text() throws InternalException
            {
                return new TextEntry(parents, "");
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public Component<@NonNull ?> bool() throws InternalException
            {
                return new BoolComponent(parents, null);
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public Component<@NonNull ?> date(DateTimeInfo dateTimeInfo) throws InternalException
            {
                switch (dateTimeInfo.getType())
                {
                    case YEARMONTHDAY:
                        return new YMD(parents, null);
                    case YEARMONTH:
                        return new YM(parents, null);
                    case TIMEOFDAY:
                        return new TimeComponent(parents, null);
                    case TIMEOFDAYZONED:
                        return new Component2<OffsetTime, LocalTime, ZoneOffset>(parents, subParents -> new TimeComponent(subParents, null), null, subParents -> new PlusMinusOffsetComponent(subParents, null), OffsetTime::of);
                    case DATETIME:
                        return new Component2<LocalDateTime, LocalDate, LocalTime>(parents, subParents -> new YMD(subParents, null), " ", subParents -> new TimeComponent(subParents, null), LocalDateTime::of);
                    case DATETIMEZONED:
                        return new Component2<ZonedDateTime, LocalDateTime, ZoneId>(parents,
                                        parents1 -> new Component2<LocalDateTime, LocalDate, LocalTime>(
                                                parents1, parents2 -> new YMD(parents2, null), " ", parents2 -> new TimeComponent(parents2, null), LocalDateTime::of),
                                        " ",
                                        parents1 -> new ZoneIdComponent(parents1, null), ZonedDateTime::of);
                }
                throw new InternalException("Unknown type: " + dateTimeInfo.getType());
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public Component<@NonNull ?> tagged(TypeId typeName, ImmutableList<TagType<DataType>> tagTypes) throws InternalException
            {
                return new TaggedComponent(parents, tagTypes, null);
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public Component<@NonNull ?> tuple(ImmutableList<DataType> types) throws InternalException
            {
                List<FXPlatformFunctionInt<ImmutableList<Component<?>>, Component<? extends Object>>> comps = new ArrayList<>(types.size());
                for (DataType type : types)
                {
                    comps.add(subParents -> component(subParents, type));
                }
                return new FixedLengthComponentList<Object[], Object>(parents, "(", ",", comps, ")", List::toArray);
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public Component<@NonNull ?> array(@Nullable DataType inner) throws InternalException
            {
                if (inner == null)
                    throw new InternalException("Can't make components for the empty list type");

                @NonNull DataType innerType = inner;
                return new VariableLengthComponentList<ListEx, Object>(parents, "[", ",", "]", ListExList::new)
                {
                    @Override
                    protected Component<? extends Object> makeNewEntry(ImmutableList<Component<?>> subParents) throws InternalException
                    {
                        return component(subParents, innerType);
                    }
                };
            }
        });
    }

    private static interface FieldMaker<V>
    {
        @OnThread(Tag.FXPlatform)
        public StructuredTextField<? extends V> make(V value) throws InternalException, UserException;
    }

    public static class DisplayCacheSTF<V> extends DisplayCache<V, StructuredTextField<? extends V>>
    {
        private final FieldMaker<V> makeField;

        public DisplayCacheSTF(GetValue<V> g, FieldMaker<V> makeField)
        {
            super(g, cs -> {}, f -> f);
            this.makeField = makeField;
        }

        @Override
        public @Nullable InputMap<?> getInputMapForParent(int rowIndex)
        {
            return null;
        }

        @Override
        public void edit(int rowIndex, @Nullable Point2D scenePoint, FXPlatformRunnable endEdit)
        {
            @Nullable StructuredTextField<? extends V> display = getRowIfShowing(rowIndex);
            if (display != null)
            {
                display.edit(scenePoint, endEdit);
            }
        }

        @Override
        public boolean isEditable()
        {
            return true;
        }

        @Override
        public boolean editHasFocus(int rowIndex)
        {
            @Nullable StructuredTextField<? extends V> display = getRowIfShowing(rowIndex);
            if (display != null)
                return display.isFocused();
            return false;
        }

        @Override
        protected StructuredTextField<? extends V> makeGraphical(int rowIndex, V value) throws InternalException, UserException
        {
            StructuredTextField<? extends V> field = makeField.make(value);
            //field.mouseTransparentProperty().bind(field.focusedProperty().not());
            return field;
        }
    }
}
