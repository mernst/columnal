package records.jellytype;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitorEx;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataType.TagType;
import records.data.datatype.NumberInfo;
import records.data.datatype.TaggedTypeDefinition.TaggedInstantiationException;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.FormatLexer;
import records.grammar.FormatParser;
import records.grammar.FormatParser.BracketedTypeContext;
import records.grammar.FormatParser.NumberContext;
import records.grammar.FormatParser.TagRefParamContext;
import records.grammar.FormatParser.TypeContext;
import records.grammar.FormatParser.UnbracketedTypeContext;
import records.loadsave.OutputBuilder;
import records.typeExp.MutVar;
import records.typeExp.TypeExp;
import records.typeExp.units.MutUnitVar;
import utility.Either;
import utility.Utility;

import java.util.function.Consumer;

/**
 * There are three different representations of types:
 *   - DataType, which is a concrete type.  No type variables, no ambiguity, everything is a specific type.
 *     Immutable.  Can always be converted into TypeExp.  There's not a need to convert it to JellyType.
 *     
 *   - TypeExp, which is used during type checking.  No named type variables, though there are mutable
 *     type variables which can be unified during the process.  Not immutable, and regenerated fresh
 *     every time we need to do a round of type checking.  Once type-checking is complete, it can be
 *     converted into a DataType (with an error if there remains problematic ambiguity).  There's no
 *     need to turn it into a JellyType.
 *     
 *   - JellyType, named for being not a concrete type.  Mirrors DataType in structure, but can contain
 *     named type variables and unit variables.  Can produce a DataType given substitution for the named
 *     variables, ditto for TypeExp.
 */
public abstract class JellyType
{
    // package-visible constructor
    JellyType()
    {
    }

    public static JellyType text()
    {
        return JellyTypePrimitive.text();
    }

    public static JellyType number(JellyUnit unit)
    {
        return new JellyTypeNumberWithUnit(unit);
    }

    public abstract TypeExp makeTypeExp(ImmutableMap<String, Either<MutUnitVar, MutVar>> typeVariables) throws InternalException;

    public abstract DataType makeDataType(ImmutableMap<String, Either<Unit, DataType>> typeVariables, TypeManager mgr) throws InternalException, UnknownTypeException, TaggedInstantiationException;
    
    //public static class TypeQuickFix {}
    
    public static class UnknownTypeException extends UserException
    {
        private final ImmutableList<JellyType> suggestedFixes;
        private final @Recorded JellyType replacementTarget;

        public UnknownTypeException(String message, JellyType replacementTarget, ImmutableList<JellyType> suggestedFixes)
        {
            super(message);
            this.replacementTarget = replacementTarget;
            this.suggestedFixes = suggestedFixes;
        }

        public ImmutableList<JellyType> getSuggestedFixes()
        {
            return suggestedFixes;
        }

        public @Recorded JellyType getReplacementTarget()
        {
            return replacementTarget;
        }
    }

    public abstract void save(OutputBuilder output);

    public abstract boolean equals(@Nullable Object o);

    public abstract int hashCode();

    // For every tagged type use anywhere within this type, call back the given function with the name.
    public abstract void forNestedTagged(Consumer<TypeId> nestedTagged);
    
    public static JellyType typeVariable(@ExpressionIdentifier String name)
    {
        return new JellyTypeIdent(name);
    }
    
    public static JellyType tuple(ImmutableList<JellyType> members)
    {
        return new JellyTypeTuple(members, true);
    }

    public static JellyType tagged(TypeId name, ImmutableList<Either<JellyUnit, JellyType>> params)
    {
        if (params.isEmpty())
            return new JellyTypeIdent(name.getRaw());
        else
            return new JellyTypeApply(name, params);
    }
    
    public static JellyType list(JellyType inner)
    {
        return new JellyTypeArray(inner);
    }
    
    public static JellyType load(TypeContext typeContext, TypeManager mgr) throws InternalException, UserException
    {
        if (typeContext.unbracketedType() != null)
            return load(typeContext.unbracketedType(), mgr);
        else if (typeContext.bracketedType() != null)
            return load(typeContext.bracketedType(), mgr);
        else
            throw new InternalException("Unrecognised case: " + typeContext);
    }

    private static JellyType load(BracketedTypeContext ctx, TypeManager mgr) throws InternalException, UserException
    {
        if (ctx.type() != null)
            return load(ctx.type(), mgr);
        else if (ctx.tuple() != null)
            return new JellyTypeTuple(Utility.mapListExI(ctx.tuple().type(), t -> load(t, mgr)), ctx.tuple().TUPLE_MORE() == null);
        throw new InternalException("Unrecognised case: " + ctx);
    }

    private static JellyType load(UnbracketedTypeContext ctx, TypeManager mgr) throws InternalException, UserException
    {
        if (ctx.BOOLEAN() != null)
            return JellyTypePrimitive.bool();
        else if (ctx.number() != null)
            return load(ctx.number(), mgr);
        else if (ctx.TEXT() != null)
            return JellyTypePrimitive.text();
        else if (ctx.date() != null)
        {
            if (ctx.date().DATETIME() != null)
                return JellyTypePrimitive.date(new DateTimeInfo(DateTimeType.DATETIME));
            else if (ctx.date().YEARMONTHDAY() != null)
                return JellyTypePrimitive.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY));
            else if (ctx.date().TIMEOFDAY() != null)
                return JellyTypePrimitive.date(new DateTimeInfo(DateTimeType.TIMEOFDAY));
            if (ctx.date().YEARMONTH() != null)
                return JellyTypePrimitive.date(new DateTimeInfo(DateTimeType.YEARMONTH));
            if (ctx.date().DATETIMEZONED() != null)
                return JellyTypePrimitive.date(new DateTimeInfo(DateTimeType.DATETIMEZONED));
        }
        else if (ctx.applyRef() != null)
        {
            @SuppressWarnings("identifier")
            TypeId typeId = new TypeId(ctx.applyRef().ident().getText());
            return new JellyTypeApply(typeId, Utility.mapListExI(ctx.applyRef().tagRefParam(), param -> load(param, mgr)));
        }
        else if (ctx.array() != null)
        {
            return new JellyTypeArray(load(ctx.array().type(), mgr));
        }
        else if (ctx.ident() != null)
        {
            // TODO is it right that @typevar comes to same place as plain ident?
            @SuppressWarnings("identifier")
            @ExpressionIdentifier String name = ctx.ident().getText();
            return new JellyTypeIdent(name);
        }
        else if (ctx.functionType() != null)
            return new JellyTypeFunction(Utility.mapListExI(ctx.functionType().functionArgs().type(), t -> load(t, mgr)), load(ctx.functionType().type(), mgr));

        throw new InternalException("Unrecognised case: " + ctx.getText());
    }

    private static Either<JellyUnit, JellyType> load(TagRefParamContext param, TypeManager mgr) throws InternalException, UserException
    {
        if (param.bracketedType() != null)
            return Either.right(load(param.bracketedType(), mgr));
        else if (param.UNIT() != null)
            // Strip curly brackets:
            return Either.left(JellyUnit.load(param.UNIT().getText().substring(1, param.UNIT().getText().length() - 1), mgr.getUnitManager()));
        else if (param.UNITVAR() != null)
            return Either.left(JellyUnit.unitVariable(param.ident().getText()));
        throw new InternalException("Unrecognised case: " + param);
    }

    private static JellyType load(NumberContext number, TypeManager mgr) throws InternalException, UserException
    {
        if (number.UNIT() == null)
            return new JellyTypeNumberWithUnit(JellyUnit.fromConcrete(Unit.SCALAR));
        else
        {
            String withCurly = number.UNIT().getText();
            return new JellyTypeNumberWithUnit(JellyUnit.load(withCurly.substring(1, withCurly.length() - 1), mgr.getUnitManager()));
        }
    }

    public static JellyType parse(String functionType, TypeManager mgr) throws UserException, InternalException
    {
        return load(Utility.<TypeContext, FormatParser>parseAsOne(functionType, FormatLexer::new, FormatParser::new, p -> p.completeType().type()), mgr);
    }

    public static interface JellyTypeVisitorEx<R, E extends Throwable>
    {
        R number(JellyUnit unit) throws InternalException, E;
        R text() throws InternalException, E;
        R date(DateTimeInfo dateTimeInfo) throws InternalException, E;
        R bool() throws InternalException, E;

        R applyTagged(TypeId typeName, ImmutableList<Either<JellyUnit, JellyType>> typeParams) throws InternalException, E;
        R tuple(ImmutableList<JellyType> inner) throws InternalException, E;
        // If null, array is empty and thus of unknown type
        R array(JellyType inner) throws InternalException, E;

        R function(ImmutableList<JellyType> argTypes, JellyType resultType) throws InternalException, E;
        
        R ident(String name) throws InternalException, E;
    }

    public static interface JellyTypeVisitor<R> extends JellyTypeVisitorEx<R, UserException>
    {

    }

    public abstract <R, E extends Throwable> R apply(JellyTypeVisitorEx<R, E> visitor) throws InternalException, E;


    public static JellyType fromConcrete(DataType t) throws InternalException
    {
        return t.apply(new DataTypeVisitorEx<JellyType, InternalException>()
        {
            @Override
            public JellyType number(NumberInfo numberInfo) throws InternalException, InternalException
            {
                return new JellyTypeNumberWithUnit(JellyUnit.fromConcrete(numberInfo.getUnit()));
            }

            @Override
            public JellyType text() throws InternalException, InternalException
            {
                return JellyTypePrimitive.text();
            }

            @Override
            public JellyType date(DateTimeInfo dateTimeInfo) throws InternalException, InternalException
            {
                return JellyTypePrimitive.date(dateTimeInfo);
            }

            @Override
            public JellyType bool() throws InternalException, InternalException
            {
                return JellyTypePrimitive.bool();
            }

            @Override
            public JellyType tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, InternalException
            {
                if (typeVars.isEmpty())
                    return new JellyTypeIdent(typeName.getRaw());
                
                return new JellyTypeApply(typeName, Utility.mapListInt(typeVars, e -> 
                    e.mapBothInt(u -> JellyUnit.fromConcrete(u), t -> fromConcrete(t))
                ));
            }

            @Override
            public JellyType tuple(ImmutableList<DataType> inner) throws InternalException, InternalException
            {
                return new JellyTypeTuple(Utility.mapListInt(inner, JellyType::fromConcrete), true);
            }

            @Override
            public JellyType array(DataType inner) throws InternalException, InternalException
            {
                return new JellyTypeArray(fromConcrete(inner));
            }

            @Override
            public JellyType function(ImmutableList<DataType> argType, DataType resultType) throws InternalException, InternalException
            {
                return new JellyTypeFunction(Utility.mapListInt(argType, a -> fromConcrete(a)), fromConcrete(resultType));
            }
        });
    }
}
