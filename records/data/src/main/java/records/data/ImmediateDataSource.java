package records.data;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.FormatLexer;
import records.grammar.MainLexer;
import records.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExFunction;
import utility.Utility;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by neil on 09/11/2016.
 */
public class ImmediateDataSource extends DataSource
{
    private final EditableRecordSet data;

    public ImmediateDataSource(TableManager mgr, EditableRecordSet data)
    {
        super(mgr, null);
        this.data = data;
    }

    public ImmediateDataSource(TableManager mgr, TableId tableId, EditableRecordSet data)
    {
        super(mgr, tableId);
        this.data = data;
    }

    @Override
    public @OnThread(Tag.Any) RecordSet getData()
    {
        return data;
    }

    @Override
    public @OnThread(Tag.Simulation) void save(@Nullable File destination, Saver then)
    {
        //dataSourceImmedate : DATA tableId BEGIN NEWLINE;
        //immediateDataLine : ITEM+ NEWLINE;
        //dataSource : (dataSourceLinkHeader | (dataSourceImmedate immediateDataLine* END DATA NEWLINE)) dataFormat;

        OutputBuilder b = new OutputBuilder();
        b.t(MainLexer.DATA).id(getId()).t(MainLexer.FORMAT).begin().nl();
        Utility.alertOnError_(() ->
        {
            for (Column c : data.getColumns())
            {
                b.t(FormatLexer.COLUMN, FormatLexer.VOCABULARY).quote(c.getName());
                c.getType().save(b, false).nl();
            }
        });
        b.end().t(MainLexer.FORMAT).nl();
        Utility.alertOnError_(() -> {
            b.t(MainLexer.VALUES).begin().nl();
            for (int i = 0; data.indexValid(i); i++)
            {
                b.indent();
                for (Column c : data.getColumns())
                    b.data(c.getType(), i);
                b.nl();
            }
        });
        b.end().t(MainLexer.VALUES).nl();
        savePosition(b);
        b.end().id(getId()).nl();
        then.saveTable(b.toString());
    }

    @Override
    public @OnThread(Tag.FXPlatform) boolean showAddColumnButton()
    {
        return true;
    }

    @Override
    public @OnThread(Tag.Any) TableOperations getOperations()
    {
        return new TableOperations(appendRowCount -> {
            Utility.alertOnError_(() ->
            {
                data.insertRows(data.getLength(), appendRowCount);
            });
        }, null /*TODO*/, (deleteRowFrom, deleteRowCount) -> {
            Utility.alertOnError_(() -> data.removeRows(deleteRowFrom, deleteRowCount));
        });
    }

    @Override
    public void addColumn(String newColumnName, DataType newColumnType, @Value Object newColumnValue) throws InternalException, UserException
    {
        data.addColumn(newColumnType.makeImmediateColumn(new ColumnId(newColumnName), Utility.replicate(data.getLength(), newColumnValue)));
    }

    @Override
    public boolean dataEquals(DataSource o)
    {
        ImmediateDataSource that = (ImmediateDataSource) o;

        return data.equals(that.data);
    }

    @Override
    public int dataHashCode()
    {
        return data.hashCode();
    }
}
