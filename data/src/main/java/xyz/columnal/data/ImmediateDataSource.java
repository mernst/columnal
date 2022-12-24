/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.data;

import annotation.qual.Value;
import xyz.columnal.id.ColumnId;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.id.TableId;
import xyz.columnal.log.ErrorHandler;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.i18n.qual.Localized;
import xyz.columnal.data.TableOperations.DeleteColumn;
import xyz.columnal.grammar.FormatLexer;
import xyz.columnal.grammar.MainLexer;
import xyz.columnal.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.TranslationUtility;

import java.io.File;

/**
 * Created by neil on 09/11/2016.
 */
public class ImmediateDataSource extends DataSource
{
    private final EditableRecordSet data;
    
    public ImmediateDataSource(TableManager mgr, InitialLoadDetails initialLoadDetails, EditableRecordSet data)
    {
        super(mgr, initialLoadDetails);
        this.data = data;
    }

    @Override
    public EditableRecordSet getData()
    {
        return data;
    }

    @Override
    public void save(File destination, Saver then, TableAndColumnRenames renames)
    {
        //dataSourceImmedate : DATA tableId BEGIN NEWLINE;
        //immediateDataLine : ITEM+ NEWLINE;
        //dataSource : (dataSourceLinkHeader | (dataSourceImmedate immediateDataLine* END DATA NEWLINE)) dataFormat;

        OutputBuilder b = new OutputBuilder();
        b.t(MainLexer.DATA).begin().raw(saveTag.getTag());
        b.pushPrefix(saveTag);
        b.nl();
        b.id(renames.tableId(getId())).nl();
        b.t(MainLexer.FORMAT, MainLexer.VOCABULARY).begin().nl();
        String errorTitle = TranslationUtility.getString("error.saving.table", getId().getRaw());
        ErrorHandler.getErrorHandler().alertOnError_(errorTitle, () ->
        {
            for (Column c : data.getColumns())
            {
                b.t(FormatLexer.COLUMN, FormatLexer.VOCABULARY).unquoted(renames.columnId(getId(), c.getName(), null).getSecond());
                b.t(FormatLexer.TYPE, FormatLexer.VOCABULARY);
                c.getType().getType().save(b);

                Object defaultValue = c.getDefaultValue();
                if (defaultValue != null)
                {
                    b.t(FormatLexer.DEFAULT, FormatLexer.VOCABULARY);
                    b.dataValue(c.getType().getType(), defaultValue);
                }
                b.nl();
            }
        });
        b.end().t(MainLexer.FORMAT).nl();
        b.t(MainLexer.VALUES).begin().nl();
        ErrorHandler.getErrorHandler().alertOnError_(errorTitle, () -> {
            for (int i = 0; data.indexValid(i); i++)
            {
                b.indent();
                boolean first = true;
                for (Column c : data.getColumns())
                {
                    if (!first)
                        b.raw(",");
                    b.data(c.getType(), i);
                    first = false;
                }
                b.nl();
            }
        });
        b.end().t(MainLexer.VALUES).nl();
        savePosition(b);
        b.pop();
        b.end().raw(saveTag.getTag()).t(MainLexer.DATA, MainLexer.VOCABULARY).nl();
        then.saveTable(b.toString());
    }

    @Override
    public TableOperations getOperations()
    {
        return new TableOperations(getManager().getRenameTableOperation(this)
        , _c -> new DeleteColumn()
        {
            @Override
            public void deleteColumn(ColumnId deleteColumnName)
            {
                data.deleteColumn(deleteColumnName);
            }
        }, appendRowCount -> {
            ErrorHandler.getErrorHandler().alertOnError_(TranslationUtility.getString("error.finding.length", getId().getRaw()), () ->
            {
                data.insertRows(data.getLength(), appendRowCount);
            });
        }, (rowIndex, insertRowCount) -> {
            data.insertRows(rowIndex, insertRowCount);
        }, (deleteRowFrom, deleteRowCount) -> {
            data.removeRows(deleteRowFrom, deleteRowCount);
        });
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

    @Override
    public TableId getSuggestedName()
    {
        return new TableId("Data");
    }
}
