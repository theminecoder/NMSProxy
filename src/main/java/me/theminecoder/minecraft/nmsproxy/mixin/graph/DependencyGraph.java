package me.theminecoder.minecraft.nmsproxy.mixin.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 *
 * Represents a graph of nodes. Every node is of DependencyGraphNode type and it has set a
 * value of the generic type <T>. It basically derives an evaluation order out
 * of its nodes. A node gets the chance to be evaluated when all the incoming
 * nodes were previously evaluated. The evaluating method of the
 * NodeValueListener is used to notify the outside of the fact that a node just
 * got the chance to be evaluated. A value of the node that is of the generic
 * type <T> is passed as argument to the evaluating method.
 *
 *
 * @author nicolae caralicea
 * @author theminecoder
 *
 * @param <T>
 */
public final class DependencyGraph<T> {
    /**
     * These are basically the nodes of the graph
     */
    private HashMap<T, DependencyGraphNode<T>> nodes = new HashMap<>();
    /**
     * The callback interface used to notify of the fact that a node just got
     * the evaluation
     */
    private Consumer<T> listener;
    /**
     * It holds a list of the already evaluated nodes
     */
    private List<DependencyGraphNode<T>> evaluatedNodes = new ArrayList<>();

    /**
     * The main constructor that has one parameter representing the callback
     * mechanism used by this class to notify when a node gets the evaluation.
     *
     * @param listener
     *            The callback interface implemented by the user classes
     */
    public DependencyGraph(Consumer<T> listener) {
        this.listener = listener;
    }

    /**
     * Allows adding of new dependicies to the graph. "evalFirstValue" needs to
     * be evaluated before "evalAfterValue"
     *
     * @param evalFirstValue
     *            The parameter that needs to be evaluated first
     * @param evalAfterValue
     *            The parameter that needs to be evaluated after
     */
    public void addDependency(T evalFirstValue, T evalAfterValue) {
        DependencyGraphNode<T> firstNode = null;
        DependencyGraphNode<T> afterNode = null;
        if (nodes.containsKey(evalFirstValue)) {
            firstNode = nodes.get(evalFirstValue);
        } else {
            firstNode = createNode(evalFirstValue);
            nodes.put(evalFirstValue, firstNode);
        }
        if (nodes.containsKey(evalAfterValue)) {
            afterNode = nodes.get(evalAfterValue);
        } else {
            afterNode = createNode(evalAfterValue);
            nodes.put(evalAfterValue, afterNode);
        }
        firstNode.addGoingOutNode(afterNode);
        afterNode.addComingInNode(firstNode);
    }

    /**
     * Creates a graph node of the <T> generic type
     *
     * @param value
     *            The value that is hosted by the node
     * @return a generic DependencyGraphNode object
     */
    private DependencyGraphNode<T> createNode(T value) {
        DependencyGraphNode<T> node = new DependencyGraphNode<T>();
        node.value = value;
        return node;
    }

    /**
     *
     * Takes all the nodes and calculates the dependency order for them.
     *
     */
    public void generateDependencies() {
        List<DependencyGraphNode<T>> orphanNodes = getOrphanNodes();
        List<DependencyGraphNode<T>> nextNodesToDisplay = new ArrayList<DependencyGraphNode<T>>();
        for (DependencyGraphNode<T> node : orphanNodes) {
            listener.accept(node.value);
            evaluatedNodes.add(node);
            nextNodesToDisplay.addAll(node.getGoingOutNodes());
        }
        generateDependencies(nextNodesToDisplay);
    }

    /**
     * Generates the dependency order of the nodes passed in as parameter
     *
     * @param nodes
     *            The nodes for which the dependency order order is executed
     */
    private void generateDependencies(List<DependencyGraphNode<T>> nodes) {
        List<DependencyGraphNode<T>> nextNodesToDisplay = null;
        for (DependencyGraphNode<T> node : nodes) {
            if (!isAlreadyEvaluated(node)) {
                List<DependencyGraphNode<T>> comingInNodes = node.getComingInNodes();
                if (areAlreadyEvaluated(comingInNodes)) {
                    listener.accept(node.value);
                    evaluatedNodes.add(node);
                    List<DependencyGraphNode<T>> goingOutNodes = node.getGoingOutNodes();
                    if (goingOutNodes != null) {
                        if (nextNodesToDisplay == null)
                            nextNodesToDisplay = new ArrayList<DependencyGraphNode<T>>();
                        // add these too, so they get a chance to be displayed
                        // as well
                        nextNodesToDisplay.addAll(goingOutNodes);
                    }
                } else {
                    if (nextNodesToDisplay == null)
                        nextNodesToDisplay = new ArrayList<DependencyGraphNode<T>>();
                    // the checked node should be carried
                    nextNodesToDisplay.add(node);
                }
            }
        }
        if (nextNodesToDisplay != null) {
            generateDependencies(nextNodesToDisplay);
        }
        // here the recursive call ends
    }

    /**
     * Checks to see if the passed in node was aready evaluated A node defined
     * as already evaluated means that its incoming nodes were already evaluated
     * as well
     *
     * @param node
     *            The Node to be checked
     * @return The return value represents the node evaluation status
     */
    private boolean isAlreadyEvaluated(DependencyGraphNode<T> node) {
        return evaluatedNodes.contains(node);
    }

    /**
     * Check to see if all the passed nodes were already evaluated. This could
     * be thought as an and logic between every node evaluation status
     *
     * @param nodes
     *            The nodes to be checked
     * @return The return value represents the evaluation status for all the
     *         nodes
     */
    private boolean areAlreadyEvaluated(List<DependencyGraphNode<T>> nodes) {
        return evaluatedNodes.containsAll(nodes);
    }

    /**
     *
     * These nodes represent the starting nodes. They are firstly evaluated.
     * They have no incoming nodes. The order they are evaluated does not
     * matter.
     *
     * @return It returns a list of graph nodes
     */
    private List<DependencyGraphNode<T>> getOrphanNodes() {
        List<DependencyGraphNode<T>> orphanNodes = null;
        Set<T> keys = nodes.keySet();
        for (T key : keys) {
            DependencyGraphNode<T> node = nodes.get(key);
            if (node.getComingInNodes() == null) {
                if (orphanNodes == null)
                    orphanNodes = new ArrayList<DependencyGraphNode<T>>();
                orphanNodes.add(node);
            }
        }
        return orphanNodes;
    }
}