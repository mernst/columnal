package utility.gui;

import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

@OnThread(Tag.FXPlatform)
public class DialogPaneWithSideButtons extends DialogPane
{
    // createButtonBar() gets called by superclass so will be non-null
    // after construction:
    @SuppressWarnings("nullness")
    private VBox buttonBar;

    private @MonotonicNonNull Map<ButtonType, Node> buttonNodes;
    
    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected Node createButtonBar(@UnknownInitialization(DialogPane.class) DialogPaneWithSideButtons this)
    {
        buttonBar = new VBox();
        buttonBar.setMaxHeight(Double.MAX_VALUE);
        buttonBar.setFillWidth(true);

        updateButtons();
        FXUtility.listen(getButtonTypes(), c -> updateButtons());

        return buttonBar;
    }

    // Part-borrowed from superclass method
    @RequiresNonNull("buttonBar")
    private void updateButtons(@UnknownInitialization(DialogPane.class) DialogPaneWithSideButtons this)
    {
        buttonBar.getChildren().clear();

        if (buttonNodes == null)
            buttonNodes = new WeakHashMap<>();
        
        boolean hasDefault = false;
        for (ButtonType cmd : getButtonTypes())
        {
            Node genButton = buttonNodes.computeIfAbsent(cmd, dialogButton -> createButton(cmd));
            
            // keep only first default button
            if (genButton instanceof Button)
            {
                ButtonData buttonType = cmd.getButtonData();

                Button button = (Button) genButton;
                button.setDefaultButton(!hasDefault && buttonType != null && buttonType.isDefaultButton());
                button.setCancelButton(buttonType != null && buttonType.isCancelButton());
                button.setMaxWidth(9999.0);

                hasDefault |= buttonType != null && buttonType.isDefaultButton();
            }
            buttonBar.getChildren().add(genButton);
        }
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected void layoutChildren()
    {
        final double leftPadding = snappedLeftInset();
        final double topPadding = snappedTopInset();
        final double rightPadding = snappedRightInset();
        final double bottomPadding = snappedBottomInset();
        
        double w = getWidth() - leftPadding - rightPadding;
        double h = getHeight() - topPadding - bottomPadding;
        
        double buttonBarWidth = buttonBar.prefWidth(h);
        double buttonBarHeight = buttonBar.minHeight(buttonBarWidth);
        // We align button bar to the bottom, to get cancel to line up with text field
        // Bit of a hack: we adjust for content's padding
        double contentBottomPadding = getContent() instanceof Pane ? ((Pane)getContent()).snappedBottomInset() : 0;
        buttonBar.resizeRelocate(leftPadding + w - buttonBarWidth, topPadding + h - buttonBarHeight - contentBottomPadding, buttonBarWidth, buttonBarHeight);
        Optional.ofNullable(getContent()).ifPresent(c -> c.resizeRelocate(leftPadding, topPadding, w - buttonBarWidth, h));
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected double computeMinWidth(double height)
    {
        return snappedLeftInset() + snappedRightInset() + Optional.ofNullable(getContent()).map(n -> n.minWidth(height)).orElse(0.0) + buttonBar.minWidth(height);
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected double computeMinHeight(double width)
    {
        return snappedTopInset() + snappedBottomInset() + Math.max(Optional.ofNullable(getContent()).map(n -> n.minHeight(width)).orElse(0.0), buttonBar.minHeight(width));
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected double computePrefWidth(double height)
    {
        return snappedLeftInset() + snappedRightInset() + Optional.ofNullable(getContent()).map(n -> n.prefWidth(height)).orElse(0.0) + buttonBar.prefWidth(height);
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected double computePrefHeight(double width)
    {
        return snappedTopInset() + snappedBottomInset() + Math.max(Optional.ofNullable(getContent()).map(n -> n.prefHeight(width)).orElse(0.0), buttonBar.prefHeight(width));
    }
}
