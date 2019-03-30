package records.gui.lexeditor;

import annotation.units.SourceLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.ErrorDetails;
import records.gui.lexeditor.Lexer.LexerResult;
import records.gui.lexeditor.TopLevelEditor.Focus;
import styled.StyledShowable;
import utility.FXPlatformConsumer;
import utility.FXPlatformRunnable;
import utility.Utility;

import java.util.ArrayList;

public class EditorContent<EXPRESSION extends StyledShowable>
{
    private LexerResult<EXPRESSION> curContent;
    private @SourceLocation int curCaretPosition;
    private final Lexer<EXPRESSION> lexer;
    private final ArrayList<FXPlatformConsumer<@SourceLocation Integer>> caretPositionListeners = new ArrayList<>();
    private final ArrayList<FXPlatformRunnable> contentListeners = new ArrayList<>();
    
    @SuppressWarnings("units")
    public EditorContent(String originalContent, Lexer<EXPRESSION> lexer)
    {
        this.lexer = lexer;
        this.curContent = this.lexer.process(originalContent);
        this.curCaretPosition = curContent.caretPositions.length > 0 ? curContent.caretPositions[0] : 0;
    }

    public void positionCaret(Focus side)
    {
        if (side == Focus.LEFT && curContent.caretPositions.length > 0)
            curCaretPosition = curContent.caretPositions[0];
        else if (side == Focus.RIGHT && curContent.caretPositions.length > 0)
            curCaretPosition = curContent.caretPositions[curContent.caretPositions.length - 1];

        for (FXPlatformConsumer<@SourceLocation Integer> caretPositionListener : caretPositionListeners)
        {
            caretPositionListener.consume(curCaretPosition);
        }
    }
    
    public int getCaretPosition()
    {
        return curCaretPosition;
    }
    
    public void replaceText(int startIncl, int endExcl, String content)
    {
        String newText = curContent.adjustedContent.substring(0, startIncl) + content + curContent.adjustedContent.substring(endExcl);
        @SuppressWarnings("units")
        @SourceLocation int newCaretPos = curCaretPosition < startIncl ? curCaretPosition : (curCaretPosition <= endExcl ? startIncl + content.length() : (curCaretPosition - (endExcl - startIncl) + content.length()));  
        this.curContent = lexer.process(newText);
        this.curCaretPosition = curContent.mapperToAdjusted.mapCaretPos(newCaretPos);
        for (FXPlatformRunnable contentListener : contentListeners)
        {
            contentListener.run();
        }
        for (FXPlatformConsumer<@SourceLocation Integer> caretPositionListener : caretPositionListeners)
        {
            caretPositionListener.consume(curCaretPosition);
        }
    }

    public String getText()
    {
        return curContent.adjustedContent;
    }

    public void addChangeListener(FXPlatformRunnable listener)
    {
        this.contentListeners.add(listener);
    }
    
    public LexerResult<EXPRESSION> getLexerResult()
    {
        return curContent;
    }

    // How many right presses (positive) or left (negative) to
    // reach nearest end of given content?
    public int _test_getCaretMoveDistance(String targetContent)
    {
        int targetStartIndex = curContent.adjustedContent.indexOf(targetContent);
        if (curContent.adjustedContent.indexOf(targetContent, targetStartIndex + 1) != -1)
            throw new RuntimeException("Content " + targetContent + " appears multiple times in editor");
        int targetEndIndex = targetStartIndex + targetContent.length();
        
        int caretIndex = Utility.findFirstIndex(Ints.asList(curContent.caretPositions), c -> c.intValue() == curCaretPosition).orElseThrow(() -> new RuntimeException("Could not find caret position"));
        if (curCaretPosition < targetStartIndex)
        {
            int hops = 0;
            while (curContent.caretPositions[caretIndex + hops] < targetStartIndex)
                hops += 1;
            return hops;
        }
        else if (curCaretPosition > targetEndIndex)
        {
            int hops = 0;
            while (curContent.caretPositions[caretIndex + hops] > targetEndIndex)
                hops -= 1;
            return hops;
        }
        return 0;
    }

    public void addCaretPositionListener(FXPlatformConsumer<@SourceLocation Integer> listener)
    {
        caretPositionListeners.add(listener);
    }

    public ImmutableList<ErrorDetails> getErrors()
    {
        return curContent.errors;
    }
}
