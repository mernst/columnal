package records.typeExp;

import com.google.common.collect.ImmutableList;
import de.uni_freiburg.informatik.ultimate.smtinterpol.util.IdentityHashSet;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import styled.StyledString;
import utility.Either;

import java.util.Objects;

public class TupleTypeExp extends TypeExp
{
    public final ImmutableList<TypeExp> knownMembers;
    // If not complete, can have many more members than knownMembers
    // e.g. "first" uses this, has single knownMembers and complete==false
    public final boolean complete;
    
    // The type classes required by this tuple, if it is not complete:
    private TypeClassRequirements requiredTypeClasses = TypeClassRequirements.empty();

    public TupleTypeExp(@Nullable ExpressionBase src, ImmutableList<TypeExp> knownMembers, boolean complete)
    {
        super(src);
        this.knownMembers = knownMembers;
        this.complete = complete;
    }

    @Override
    public boolean containsMutVar(MutVar mutVar)
    {
        return knownMembers.stream().anyMatch(t -> t.containsMutVar(mutVar));
    }

    @Override
    protected Either<TypeConcretisationError, DataType> _concrete(TypeManager typeManager) throws InternalException, UserException
    {
        if (!complete)
        {
            @Nullable DataType assumingComplete = Either.mapMEx(knownMembers, (TypeExp t) -> t.toConcreteType(typeManager)).map(ts -> DataType.tuple(ts)).<@Nullable DataType>either(err -> null, t -> t);
            return Either.left(new TypeConcretisationError(StyledString.s("Error: tuple of indeterminate size"), assumingComplete));
        }
        return Either.mapMEx(knownMembers, (TypeExp t) -> t.toConcreteType(typeManager)).map(ts -> DataType.tuple(ts));
    }

    @Override
    public @Nullable StyledString requireTypeClasses(TypeClassRequirements typeClasses, IdentityHashSet<MutVar> visited)
    {
        for (TypeExp member : knownMembers)
        {
            @Nullable StyledString err = member.requireTypeClasses(typeClasses, visited);
            if (err != null)
                return err;
        }
        if (!complete)
        {
            this.requiredTypeClasses = TypeClassRequirements.union(this.requiredTypeClasses, typeClasses);
        }
        return null;
    }

    @Override
    public Either<StyledString, TypeExp> _unify(TypeExp b) throws InternalException
    {
        if (!(b instanceof TupleTypeExp))
            return typeMismatch(b);

        TupleTypeExp bt = (TupleTypeExp) b;
        
        // Length mismatch is allowed only if the shorter one is marked as
        // incomplete.  Check for the bad case (shorter is marked as complete)
        if (knownMembers.size() < bt.knownMembers.size() && complete)
        {
            return Either.left(StyledString.s("Mismatch: tuple of size at least " + bt.knownMembers.size() + " expected, vs tuple of size: " + knownMembers.size()));
        }
        else if (knownMembers.size() > bt.knownMembers.size() && bt.complete)
        {
            return Either.left(StyledString.s("Mismatch: tuple of size at least " + knownMembers.size() + " expected, vs tuple of size: " + knownMembers.size()));
        }
        // Other bad case: both are complete, and different sizes:
        else if (knownMembers.size() != bt.knownMembers.size() && complete && bt.complete)
        {
            return Either.left(StyledString.s("Mismatch: tuple of size " + knownMembers.size() + " expected, vs tuple of size: " + bt.knownMembers.size()));
        }
        
        // If we're still here, we can just unify along the length of the shortest.  Then we fill in remaining args from the longer side
        ImmutableList.Builder<TypeExp> unified = ImmutableList.builder();
        int longer = Math.max(knownMembers.size(), bt.knownMembers.size());
        for (int i = 0; i < longer; i++)
        {
            if (i < knownMembers.size() && i < bt.knownMembers.size())
            {
                Either<StyledString, TypeExp> result = knownMembers.get(i).unifyWith(bt.knownMembers.get(i));
                if (result.isLeft())
                    return result;
                unified.add(result.getRight("Impossible"));
            }
            else if (i < knownMembers.size())
            {
                @Nullable StyledString err = knownMembers.get(i).requireTypeClasses(bt.requiredTypeClasses);
                if (err != null)
                    return Either.left(err);
                unified.add(knownMembers.get(i));
            }
            else
            {
                @Nullable StyledString err = bt.knownMembers.get(i).requireTypeClasses(requiredTypeClasses);
                if (err != null)
                    return Either.left(err);
                unified.add(bt.knownMembers.get(i));
            }
        }
        return Either.right(new TupleTypeExp(src != null ? src : b.src, unified.build(), complete || bt.complete));
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TupleTypeExp that = (TupleTypeExp) o;
        return complete == that.complete &&
            Objects.equals(knownMembers, that.knownMembers);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(knownMembers, complete);
    }

    @Override
    public StyledString toStyledString(int maxDepth)
    {
        return StyledString.concat(StyledString.s("("), 
            StyledString.intercalate(StyledString.s(", "), knownMembers.stream().map(t -> t.toStyledString(maxDepth)).collect(ImmutableList.<StyledString>toImmutableList())),
            StyledString.s(complete ? ")" : ", ...)"));
    }
}

