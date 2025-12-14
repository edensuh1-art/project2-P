import java.util.*;
//EDEN SUH
//10-5-25
//CODEX AI USED


/**
 * Dynamic-programming strategy for picking the best order to rob forts.
 * Basically: treat the graph like a forest, run DFS, and at each fort decide
 * “should the kids go before me or after me?” while keeping track of alert rules.
 */
public class DPStrategy implements RobbingStrategy {

    @Override
    public List<String> chooseOrderToAttack(LabeledValueGraph graph) {
        List<String> attackOrder = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        // The graph can have multiple components, so hit each root once.
        for (String root : graph.getAllVertexLabels()) {
            if (visited.contains(root)) continue;

            // DFS returns the DP results for that whole subtree.
            NodeDP dp = dfs(root, null, graph, visited);

            // We want the “not already alerted” version at the root.
            attackOrder.addAll(dp.bestNoAlertOrder);
        }

        return attackOrder;
    }

    /**
     * Holds all the per-node DP info.
     */
    private static class NodeDP {
        final String label;
        final boolean hasShield;  // shield fort doesn’t alert neighbors
        final boolean immune;     // immune fort alerts don’t half its gold
        final boolean selfAlert;  // self-alert always triggers alert on itself
        double bestNoAlertValue;
        List<String> bestNoAlertOrder;
        double bestAlertValue;
        List<String> bestAlertOrder;

        NodeDP(String label) {
            this.label = label;
            // Decode the magic symbols from the project spec
            this.hasShield = label.contains("" + (char) (AttackValueVerifier.obfuscated[2] >> 1));
            this.immune    = label.contains("" + (char) (AttackValueVerifier.obfuscated[1] << 1));
            this.selfAlert = label.contains("" + (char) (AttackValueVerifier.obfuscated[0] >> 1));
        }
    }

    /**
     * Standard DFS over the tree. For each node, gather its children’s DP data
     * and compute our DP (best before/after orders for alerted / not alerted).
     */
    private NodeDP dfs(String label, String parent, LabeledValueGraph graph, Set<String> visited) {
        visited.add(label);
        NodeDP current = new NodeDP(label);

        List<NodeDP> children = new ArrayList<>();
        for (String neighbor : graph.getAdjacentVertices(label)) {
            if (neighbor.equals(parent)) continue;
            if (visited.contains(neighbor)) continue; // safety in case of weird input
            children.add(dfs(neighbor, label, graph, visited));
        }

        computeBestOrders(current, children, graph);
        return current;
    }

    /**
     * Fills in the 2 DP states: (1) root is NOT initially alerted (2) root IS initially alerted.
     */
    private void computeBestOrders(NodeDP node, List<NodeDP> children, LabeledValueGraph graph) {
        // root starts unalerted
        OrderResult noAlert = computeBestOrderForInitialAlert(node, children, false, graph);
        node.bestNoAlertOrder = noAlert.order;
        node.bestNoAlertValue = noAlert.value;

        // root starts already alerted
        OrderResult yesAlert = computeBestOrderForInitialAlert(node, children, true, graph);
        node.bestAlertOrder = yesAlert.order;
        node.bestAlertValue = yesAlert.value;
    }

    private static class OrderResult {
        final List<String> order; // actual attack sequence
        final double value;       // total gold we get for that order
        OrderResult(List<String> order, double value) {
            this.order = order;
            this.value = value;
        }
    }

    /**
     * The “hard part” — we try to partition children into:
     *    - some that go BEFORE parent
     *    - some that go AFTER parent
     *
     * And we track whether any before-parent child would alert the parent.
     * This is the DP merge step.
     */
    private OrderResult computeBestOrderForInitialAlert(NodeDP node, List<NodeDP> children,
                                                        boolean initialAlert, LabeledValueGraph graph) {

        // Start with one empty partition no children assigned yet.
        List<PartitionState> states = new ArrayList<>();
        states.add(PartitionState.empty());

        // Process each child one at a time
        for (NodeDP child : children) {
            List<PartitionState> nextStates = new ArrayList<>();

            for (PartitionState state : states) {

                // Option 1 child goes BEFORE the parent
                boolean alertedByChild = state.alertedBeforeParent || !child.hasShield;
                double beforeValue = state.beforeValue + child.bestNoAlertValue;

                List<String> beforeOrder = new ArrayList<>(state.beforeOrder);
                beforeOrder.addAll(child.bestNoAlertOrder);

                PartitionState beforeState = new PartitionState(
                        alertedByChild,
                        beforeValue,
                        beforeOrder,
                        state.afterValue,
                        new ArrayList<>(state.afterOrder)
                );
                nextStates = chooseBetter(nextStates, beforeState);

                // Option 2: child goes AFTER the parent
                // If the parent has a shield child won’t be alerted. Otherwise use the child’s alert DP state
                double afterValue = state.afterValue + (node.hasShield ? child.bestNoAlertValue : child.bestAlertValue);

                List<String> afterOrder = new ArrayList<>(state.afterOrder);
                afterOrder.addAll(node.hasShield ? child.bestNoAlertOrder : child.bestAlertOrder);

                PartitionState afterState = new PartitionState(
                        state.alertedBeforeParent,
                        state.beforeValue,
                        new ArrayList<>(state.beforeOrder),
                        afterValue,
                        afterOrder
                );
                nextStates = chooseBetter(nextStates, afterState);
            }

            states = nextStates;
        }

        // Now that all children are assigned decide how the parent behaves
        OrderResult bestResult = null;
        for (PartitionState state : states) {

            // Parent is alerted if initial alert or self-alert or any before child alerted it
            boolean alertAtNode = initialAlert || node.selfAlert || state.alertedBeforeParent;

            double parentGold = computeGold(node.label, alertAtNode, node.immune, graph);
            double total = state.beforeValue + parentGold + state.afterValue;

            // Build final ordering
            List<String> ordering = new ArrayList<>(state.beforeOrder);
            ordering.add(node.label);
            ordering.addAll(state.afterOrder);

            if (bestResult == null || total > bestResult.value) {
                bestResult = new OrderResult(ordering, total);
            }
        }

        // Safety fallback leaf node
        if (bestResult == null) {
            return new OrderResult(
                    Collections.singletonList(node.label),
                    computeGold(node.label, initialAlert || node.selfAlert, node.immune, graph)
            );
        }

        return bestResult;
    }

    /**
     * Basic gold computation. If the fort is alerted and NOT immune → half gold.
     */
    private double computeGold(String label, boolean alerted, boolean immune, LabeledValueGraph graph) {
        double gold = graph.getValueAt(label);
        if (alerted && !immune) gold /= 2.0;
        return gold;
    }

    /**
     * A partition of children into “before parent” and “after parent”.
     * We only track:
     *   - whether parent gets alerted by these before-children
     *   - sum of gold before and after
     *   - the corresponding orders
     */
    private static class PartitionState {
        final boolean alertedBeforeParent;
        final double beforeValue;
        final List<String> beforeOrder;
        final double afterValue;
        final List<String> afterOrder;

        PartitionState(boolean alertedBeforeParent, double beforeValue, List<String> beforeOrder,
                       double afterValue, List<String> afterOrder) {
            this.alertedBeforeParent = alertedBeforeParent;
            this.beforeValue = beforeValue;
            this.beforeOrder = beforeOrder;
            this.afterValue = afterValue;
            this.afterOrder = afterOrder;
        }

        static PartitionState empty() {
            return new PartitionState(false, 0.0, new ArrayList<>(), 0.0, new ArrayList<>());
        }

        double combined() {
            return beforeValue + afterValue;
        }
    }

    /**
     * We only keep ONE state for “alertedBeforeParent=true” and ONE for “false”.
     * If the new one is strictly better (higher gold), it replaces the existing state.
     */
    private List<PartitionState> chooseBetter(List<PartitionState> states, PartitionState candidate) {
        boolean replaced = false;

        for (int i = 0; i < states.size(); i++) {
            PartitionState s = states.get(i);

            // They belong to the same kind
            if (s.alertedBeforeParent == candidate.alertedBeforeParent) {
                // Keep the better scoring of the two
                if (candidate.combined() > s.combined()) {
                    states.set(i, candidate);
                }
                replaced = true;
                break;
            }
        }

        // No matching state existed add it
        if (!replaced) states.add(candidate);

        return states;
    }
}
