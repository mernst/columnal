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

package xyz.columnal.utility;

import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Created by neil on 21/11/2016.
 */
public class GraphUtility
{
    /**
     * Collapses a directed acyclic graph into a linear list of nodes, such that all edges
     * point forwards in the list.  The nodes in putAsLateAsPossible are put as late in the list
     * as possible (if there are multiple orderings available).
     *
     * @param nodes The full set of nodes in the graph, which will be returned, but maybe in a different order
     * @param incomingEdges A map from a node to its list of *incoming* edges.  Nodes with no incoming may be absent or map to an empty list.
     * @param putAsLateAsPossible See above
     * @param <T> The node type.  Nodes are compared using .equals
     * @return The flattened ordered list of nodes.
     */
    public static <T> List<T> lineariseDAG(Collection<T> nodes, Map<T, ? extends Collection<T>> incomingEdges, Collection<T> putAsLateAsPossible)
    {
        // Kahn's algorithm, from wikipedia:
        //   L ← Empty list that will contain the sorted elements
        //   S ← Set of all nodes with no incoming edges
        //   while S is non-empty do
        //     remove a node n from S
        //     add n to tail of L
        //     for each node m with an edge e from n to m do
        //        remove edge e from the graph
        //        if m has no other incoming edges then
        //           insert m into S

        // We don't have any empty lists in the map:
        Map<T, HashSet<T>> remainingEdges = new HashMap<>();
        for (Entry<T, ? extends Collection<T>> origEdge : incomingEdges.entrySet())
        {
            if (!origEdge.getValue().isEmpty())
                remainingEdges.put(origEdge.getKey(), new HashSet<>(origEdge.getValue()));
        }

        List<T> l = new ArrayList<T>();
        List<T> s = new ArrayList<T>(nodes);
        for (Iterator<T> iterator = s.iterator(); iterator.hasNext(); )
        {
            T t = iterator.next();
            Collection<T> incoming = remainingEdges.get(t);
            if (incoming != null) // List is not empty given above list, so no need to check size
                iterator.remove(); // Has an incoming edge; shouldn't be in s.
        }

        while (!s.isEmpty())
        {
            T next = null;
            // Faster to remove from end:
            for (int i = s.size() - 1; i >= 0; i--)
            {
                if (!putAsLateAsPossible.contains(s.get(i)))
                {
                    next = s.remove(i);
                    break;
                }
            }
            // Have to take a late one:
            if (next == null)
                next = s.remove(s.size() - 1); // Faster to remove from end
            l.add(next);
            for (Iterator<Entry<T, HashSet<T>>> iterator = remainingEdges.entrySet().iterator(); iterator.hasNext(); )
            {
                Entry<T, HashSet<T>> incoming = iterator.next();
                incoming.getValue().remove(next);
                if (incoming.getValue().isEmpty())
                {
                    s.add(incoming.getKey());
                    iterator.remove();
                }
            }
        }

        return l;
    }
}
