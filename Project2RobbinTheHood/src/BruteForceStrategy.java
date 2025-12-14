import java.util.*;
//EDEN SUH
//10-5-25
//CODEX AI USED

/**
 * An exhaustive search/backtracking strategy to select vertices for robbing.
 * (Basically: try every possible order and keep whatever makes the most gold.)
 */
public class BruteForceStrategy implements RobbingStrategy {

    // Keep track of the best attack order we’ve found so far
    // Starts off empty and gets replaced as we find better ones.
    List<String> bestOrderingFound = new ArrayList<>();
    double bestValueFound = Double.NEGATIVE_INFINITY;

    @Override
    public List<String> chooseOrderToAttack(LabeledValueGraph graph) {

        List<String> allVertexLabels = graph.getAllVertexLabels();
        List<String> chosen = new ArrayList<>(); // our current partial ordering while we recurse

        // Kick off the recursive brute-force search
        tryAllPermutations(graph, allVertexLabels, chosen);

        // After trying literally everything, we return whatever gave the most gold
        return bestOrderingFound;
    }

    private void tryAllPermutations(LabeledValueGraph graph,
                                    List<String> remainingVertexLabels,
                                    List<String> chosen) {

        // If there’s nothing left to pick, we’ve built a full order score it
        if (remainingVertexLabels.isEmpty()) {

            // Compute how much gold this exact ordering earns
            double chosenValue = AttackValueVerifier.computeGoldForAttackOrdering(graph, chosen);

            // Update “best” if this one beats what we had
            if (chosenValue > bestValueFound) {
                bestValueFound = chosenValue;
                bestOrderingFound = new ArrayList<>(chosen); // copy it so it doesn’t mutate later
            }

        } else {

            // Try each remaining vertex one-by-one in the next slot of the ordering
            for (int i = 0; i < remainingVertexLabels.size(); i++) {

                // Pick a vertex
                String chosenLabel = remainingVertexLabels.remove(i);
                chosen.add(chosenLabel);

                // Recurse with that choice locked in
                tryAllPermutations(graph, remainingVertexLabels, chosen);

                // Undo the choices so we can try the next option
                chosen.remove(chosen.size() - 1);
                remainingVertexLabels.add(i, chosenLabel);
            }
        }
    }
}
