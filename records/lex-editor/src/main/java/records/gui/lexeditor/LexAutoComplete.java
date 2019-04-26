package records.gui.lexeditor;

import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PopupControl;
import javafx.scene.control.Skin;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.web.WebView;
import javafx.util.Duration;
import log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.GUI;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@OnThread(Tag.FXPlatform)
public class LexAutoComplete
{
    private final LexAutoCompleteWindow window = new LexAutoCompleteWindow();
    private final EditorDisplay editor;
    private final Timeline updatePosition;
    private final FXPlatformRunnable triggerCompletion;

    public LexAutoComplete(@UnknownInitialization EditorDisplay editor, FXPlatformRunnable triggerCompletion)
    {
        this.editor = Utility.later(editor);
        this.triggerCompletion = triggerCompletion;
        this.updatePosition = new Timeline(new KeyFrame(Duration.millis(250), e -> {
            Utility.later(this).updateWindowPosition(Utility.later(this).window.listView.getItems());
        }));
    }

    public void show(ImmutableList<LexCompletion> completions)
    {
        window.setCompletions(completions);
        FXUtility.runAfterNextLayout(() -> updateWindowPosition(completions));
        updatePosition.playFromStart();
    }

    private void updateWindowPosition(List<LexCompletion> completions)
    {
        Point2D caretBottom = editor.getCaretBottomOnScreen(completions.stream().mapToInt(c -> c.startPos).min().orElse(editor.getCaretPosition()));
        double labelPad = window.listView.getTotalTextLeftPad() + 1;
        if (caretBottom != null && !completions.isEmpty())
            window.show(editor, caretBottom.getX() - labelPad, caretBottom.getY());
        else
            hide();
    }

    public void hide()
    {
        window.hide();
        window.setCompletions(ImmutableList.of());
        updatePosition.stop();
    }

    public boolean isShowing()
    {
        return window.isShowing();
    }

    public void down()
    {
        int sel = window.listView.getSelectedIndex();
        if (sel + 1 < window.listView.getItems().size())
            window.listView.select(sel + 1);
    }

    public void up()
    {
        int sel = window.listView.getSelectedIndex();
        if (sel - 1 >= 0)
            window.listView.select(sel - 1);
        else
            window.listView.select(-1);
    }

    public Optional<LexCompletion> selectCompletion()
    {
        return Optional.ofNullable(window.listView.getSelectedItem());
    }
    
    public static Optional<LexCompletion> matchWordStart(String src, @CanonicalLocation int startPos, String completionText)
    {
        int curCompletionStart = 0;
        do
        {
            if (Utility.startsWithIgnoreCase(completionText, src, curCompletionStart))
            {
                return Optional.of(new LexCompletion(startPos, completionText));
            }
            curCompletionStart = completionText.indexOf(' ', curCompletionStart);
            if (curCompletionStart >= 0)
                curCompletionStart += 1;
        }
        while (curCompletionStart >= 0);
        
        return Optional.empty();
    }
    
    public enum LexSelectionBehaviour
    {
        SELECT_IF_ONLY,
        SELECT_IF_TOP,
        NO_AUTO_SELECT;
    }

    // Note -- the with methods modify this class in-place.  This shouldn't be an issue as the methods are only
    // used in a builder style.
    public static class LexCompletion
    {
        public final @CanonicalLocation int startPos;
        public String content;
        public StyledString display;
        public int relativeCaretPos;
        public LexSelectionBehaviour selectionBehaviour;
        // HTML file name (e.g. function-abs.html) and optional anchor
        public @Nullable Pair<String, @Nullable String> furtherDetailsURL;

        private LexCompletion(@CanonicalLocation int startPos, String content, int relativeCaretPos, LexSelectionBehaviour selectionBehaviour, @Nullable Pair<String, @Nullable String> furtherDetailsURL)
        {
            this.startPos = startPos;
            this.content = content;
            this.display = StyledString.s(content);
            this.relativeCaretPos = relativeCaretPos;
            this.selectionBehaviour = selectionBehaviour;
            this.furtherDetailsURL = furtherDetailsURL;
        }

        public LexCompletion(@CanonicalLocation int startPos, String content)
        {
            this(startPos, content, content.length(), LexSelectionBehaviour.NO_AUTO_SELECT, null);
        }
        
        public LexCompletion withReplacement(String newContent)
        {
            this.content = newContent;
            this.display = StyledString.s(newContent);
            return this;
        }
        
        public LexCompletion withCaretPosAfterCompletion(int pos)
        {
            this.relativeCaretPos = pos;
            return this;
        }
        
        public LexCompletion withSelectionBehaviour(LexSelectionBehaviour selectionBehaviour)
        {
            this.selectionBehaviour = selectionBehaviour;
            return this;
        }
        
        public LexCompletion withFurtherDetailsURL(@Nullable String url)
        {
            this.furtherDetailsURL = url == null ? null : new Pair<>(url, null);
            return this;
        }
    }

    @OnThread(Tag.FXPlatform)
    public class LexAutoCompleteWindow extends PopupControl
    {
        private final HBox pane;
        private final LexCompletionList listView;
        
        public LexAutoCompleteWindow()
        {
            this.listView = new LexCompletionList();
            WebView webView = new WebView();
            webView.setFocusTraversable(false);
            webView.setPrefWidth(400.0);
            webView.setVisible(false);
            listView.setMaxHeight(400.0);
            webView.setMaxHeight(400.0);
            this.pane = new HBox(listView, webView);
            pane.setFillHeight(false);
            
            setAutoFix(false);
            setAutoHide(false);
            setHideOnEscape(false);
            setSkin(new LexAutoCompleteSkin());
            pane.getStylesheets().add(FXUtility.getStylesheet("autocomplete.css"));
            pane.getStyleClass().add("lex-complete-root");
            if (getScene() != null)
            {
                getScene().addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
                    if (e.getButton() == MouseButton.MIDDLE)
                    {
                        hide();
                        e.consume();
                    }
                });
            }
            FXUtility.addChangeListenerPlatform(listView.selectedItemProperty(), selected -> {
                if (selected != null)
                {
                    @Nullable Pair<String, @Nullable String> fileNameAndAnchor = selected.furtherDetailsURL;
                    if (fileNameAndAnchor != null)
                    {
                        URL url = getClass().getResource("/" + fileNameAndAnchor.getFirst());
                        if (url != null)
                        {
                            webView.getEngine().load(url.toExternalForm() + (fileNameAndAnchor.getSecond() != null ? "#" + fileNameAndAnchor.getSecond() : ""));
                            webView.setVisible(true);
                        }
                        else
                        {
                            Log.error("Missing file: " + fileNameAndAnchor.getFirst());
                            webView.setVisible(false);
                        }
                    }
                    else
                        webView.setVisible(false);
                }
                else
                    webView.setVisible(false);
            });
        }

        public void setCompletions(ImmutableList<LexCompletion> completions)
        {
            this.listView.setCompletions(completions);
            if ((completions.size() == 1 && completions.get(0).selectionBehaviour == LexSelectionBehaviour.SELECT_IF_ONLY)
                || (completions.size() >= 1 && completions.get(0).selectionBehaviour == LexSelectionBehaviour.SELECT_IF_TOP))
            {
                listView.select(0);
            }
        }

        @OnThread(Tag.FXPlatform)
        public @Nullable String _test_getSelectedContent()
        {
            return Utility.onNullable(listView.getSelectedItem(), l -> l.content);
        }

        public List<LexCompletion> _test_getShowing()
        {
            return listView.getItems();
        }

        @OnThread(Tag.FX)
        private class LexAutoCompleteSkin implements Skin<LexAutoCompleteWindow>
        {
            @Override
            public LexAutoCompleteWindow getSkinnable()
            {
                return LexAutoCompleteWindow.this;
            }

            @Override
            public Node getNode()
            {
                return pane;
            }

            @Override
            public void dispose()
            {

            }
        }
    }

    // Like a ListView, but with customisations to allow pinned "related" section
    @OnThread(Tag.FXPlatform)
    private class LexCompletionList extends Region
    {
        // The completions change all at once, so a mutable ImmutableList reference:
        private ImmutableList<LexCompletion> curCompletions = ImmutableList.of();
        
        // This map's values are always inserted into the children of this region
        private ObservableMap<LexCompletion, TextFlow> visible = FXCollections.observableMap(new IdentityHashMap<>());
        
        private int selectionIndex;
        // Holds a mirror of selectionIndex:
        private final ObjectProperty<@Nullable LexCompletion> selectedItem = new SimpleObjectProperty<>(null);
        private int topVisibleItemIndex = 0;
        private double topVisibleItemScroll = 0;
        private final double ITEM_HEIGHT = 24;
        
        public LexCompletionList()
        {
            getStyleClass().add("lex-completion-list");
            setFocusTraversable(false);
            visible.addListener((MapChangeListener<LexCompletion, TextFlow>) (MapChangeListener.Change<? extends LexCompletion, ? extends TextFlow> c) -> {
                if (c.wasRemoved())
                    getChildren().remove(c.getValueRemoved());
                if (c.wasAdded())
                    getChildren().add(c.getValueAdded());
            });
            
            setPrefWidth(300.0);
            setMaxHeight(USE_PREF_SIZE);
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        protected double computePrefHeight(double width)
        {
            return Math.min(curCompletions.size(), 16) * ITEM_HEIGHT;
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        protected void layoutChildren()
        {
            double y = topVisibleItemScroll;
            Insets insets = getInsets();
            for (int i = topVisibleItemIndex; i < curCompletions.size() && y < getHeight(); i++, y += ITEM_HEIGHT)
            {
                TextFlow flow = visible.get(curCompletions.get(i));
                if (flow != null)
                {
                    // Should always be non-null, but need to guard
                    flow.resizeRelocate(insets.getLeft(), y, getWidth() - insets.getRight() - insets.getLeft(), ITEM_HEIGHT);
                }
            }
        }
        
        private void recalculateChildren()
        {
            Set<LexCompletion> toKeep = Sets.newIdentityHashSet(); 
            double y = topVisibleItemScroll;
            for (int i = topVisibleItemIndex; i < curCompletions.size() && y < getHeight(); i++, y += ITEM_HEIGHT)
            {
                TextFlow item = visible.computeIfAbsent(curCompletions.get(i), this::makeFlow);
                FXUtility.setPseudoclass(item, "selected", selectionIndex == i);
                toKeep.add(curCompletions.get(i));
            }
            visible.entrySet().removeIf(e -> !toKeep.contains(e.getKey()));
            requestLayout();
        }

        private TextFlow makeFlow(LexCompletion lexCompletion)
        {
            TextFlow textFlow = new TextFlow();
            textFlow.getChildren().setAll(lexCompletion.display.toGUI());
            textFlow.getStyleClass().add("lex-completion");
            textFlow.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY)
                {
                    if (e.getClickCount() >= 1)
                        select(Utility.findFirstIndex(curCompletions, c -> c == lexCompletion).orElse(-1));
                    
                    if (e.getClickCount() == 2 && selectionIndex != -1)
                        triggerCompletion.run();
                }
            });
            return textFlow;
        }

        public void setCompletions(ImmutableList<LexCompletion> completions)
        {
            this.curCompletions = completions;
            // Also scroll to top:
            topVisibleItemIndex = 0;
            topVisibleItemScroll = 0;
            recalculateChildren();
            select(0);
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public void requestFocus()
        {
            // Can't be focused
        }
        
        public void select(int itemIndex)
        {
            selectionIndex = Math.min(curCompletions.size() - 1, itemIndex);
            selectedItem.setValue(selectionIndex < 0 ? null : curCompletions.get(selectionIndex));
            // Update graphics:
            recalculateChildren();
        }
        
        public @Nullable LexCompletion getSelectedItem()
        {
            return selectedItem.get();
        }
        
        public int getSelectedIndex()
        {
            return selectionIndex;
        }

        public ImmutableList<LexCompletion> getItems()
        {
            return curCompletions;
        }

        public ObjectExpression<@Nullable LexCompletion> selectedItemProperty()
        {
            return selectedItem;
        }

        public double getTotalTextLeftPad()
        {
            return getInsets().getLeft() + ((Region)getParent()).getInsets().getLeft() + visible.values().stream().findFirst().map(f -> f.getInsets().getLeft()).orElse(0.0);
        }
    }
}
