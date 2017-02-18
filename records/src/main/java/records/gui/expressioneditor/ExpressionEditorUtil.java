package records.gui.expressioneditor;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by neil on 21/01/2017.
 */
public class ExpressionEditorUtil
{
    @NotNull
    protected static VBox withLabelAbove(TextField textField, String cssClass, String label)
    {
        textField.getStyleClass().addAll(cssClass + "-name", "labelled-name");
        Label typeLabel = new Label(label);
        typeLabel.getStyleClass().addAll(cssClass + "-top", "labelled-top");
        VBox vBox = new VBox(typeLabel, textField);
        vBox.getStyleClass().add(cssClass);
        return vBox;
    }

    public static void setStyles(Label topLabel, Stream<String> parentStyles)
    {
        topLabel.getStyleClass().add(parentStyles.collect(Collectors.joining("-")) + "-child");
    }
}
