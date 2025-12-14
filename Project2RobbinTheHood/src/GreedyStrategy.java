import java.util.*;
//EDEN SUH
//10-5-25
//CODEX AI USED

/**
 * A greedy algorithm to select vertices for robbing.
 * Basically: keep picking the "best looking" fort at each step,
 * updating scores as alerts spread.
 */
public class GreedyStrategy implements RobbingStrategy {

    // a helper class so we don't keep reparsing labels.
    // Stores all the info we care about for each fort
    private static class FortInfo {
        final double value;      // gold value
        final boolean immune;    // can't lose gold from alerts
        final boolean selfAlert; // goes on alert the moment you hit it
        final boolean shield;    // doesn't trigger alerts in neighbors
        final List<String> neighbors;

        FortInfo(String label,
                 char immuneChar,
                 char selfAlertChar,
                 char shieldChar,
                 List<String> neighbors,
                 double value) {
            this.value = value;
            this.immune = label.contains("" + immuneChar);
            this.selfAlert = label.contains("" + selfAlertChar);
            this.shield = label.contains("" + shieldChar);
            this.neighbors = neighbors;
        }
    }

    // This is what goes into the priority queue
    // The version is a trick to know whether an entry is outdated
    private static class Entry {
        final String label;
        final int version;
        final double score;

        Entry(String label, int version, double score) {
            this.label = label;
            this.version = version;
            this.score = score;
        }
    }

    @Override
    public List<String> chooseOrderToAttack(LabeledValueGraph graph) {

        List<String> vertices = graph.getAllVertexLabels();
        List<String> attackOrder = new ArrayList<>(vertices.size());

        // Keep track of forts we've already robbed + any that are currently on high alert
        Set<String> alreadyAttacked = new HashSet<>();
        Set<String> highAlertForts = new HashSet<>();

        // Decode the special characters the instructor hid in the obfuscated array
        char selfAlertChar = (char) (AttackValueVerifier.obfuscated[0] >> 1);
        char immuneChar = (char) (AttackValueVerifier.obfuscated[1] << 1);
        char shieldChar = (char) (AttackValueVerifier.obfuscated[2] >> 1);

        // Preload all fort info so we don't keep asking the graph for stuff
        Map<String, FortInfo> infoMap = new HashMap<>();
        for (String v : vertices) {
            infoMap.put(v, new FortInfo(v, immuneChar, selfAlertChar, shieldChar,
                    graph.getAdjacentVertices(v), graph.getValueAt(v)));
        }

        // Each vertex gets a version number so we know when PQ entries are stale
        Map<String, Integer> vertexVersion = new HashMap<>();
        PriorityQueue<Entry> pq = new PriorityQueue<>(Comparator.comparingDouble((Entry e) -> e.score).reversed());

        // Initialize the queue with a score guess for each fort
        for (String v : vertices) {
            vertexVersion.put(v, 0);
            pq.add(new Entry(v, 0, computeScore(v, infoMap, highAlertForts)));
        }

        // Main greedy loop, pick the best scoring fort at each step
        while (attackOrder.size() < vertices.size()) {
            Entry best = pq.poll();
            if (best == null) break; // shouldn't happen

            // If we've already robbed this one, skip it
            if (alreadyAttacked.contains(best.label)) continue;

            // If PQ entry is outdated, recalc and reinsert instead of using it
            if (best.version != vertexVersion.get(best.label)) {
                int currentVersion = vertexVersion.get(best.label);
                pq.add(new Entry(best.label, currentVersion, computeScore(best.label, infoMap, highAlertForts)));
                continue;
            }

            // Lock in the choice
            attackOrder.add(best.label);
            alreadyAttacked.add(best.label);

            FortInfo selectedInfo = infoMap.get(best.label);

            // Self alert fort goes on alert immediately
            if (selectedInfo.selfAlert) {
                if (highAlertForts.add(best.label)) {
                    updateScoresForAlert(best.label, infoMap, highAlertForts, vertexVersion, pq);
                }
            }

            // If it's not a shield fort, hitting it can alert its neighbors.
            if (!selectedInfo.shield) {
                for (String neighbor : selectedInfo.neighbors) {
                    if (highAlertForts.add(neighbor)) {
                        updateScoresForAlert(neighbor, infoMap, highAlertForts, vertexVersion, pq);
                    }
                }
            }
        }

        return attackOrder;
    }

    // When something goes on alert, a bunch of scores change,
    // so this bumps version numbers and pushes new PQ entries.
    private void updateScoresForAlert(String alerted,
                                      Map<String, FortInfo> infoMap,
                                      Set<String> highAlertForts,
                                      Map<String, Integer> vertexVersion,
                                      PriorityQueue<Entry> pq) {

        // Update the alerted fort
        int newVersion = vertexVersion.get(alerted) + 1;
        vertexVersion.put(alerted, newVersion);
        pq.add(new Entry(alerted, newVersion, computeScore(alerted, infoMap, highAlertForts)));

        // Also update its neighbors, since their penalty/gold values might change now
        for (String neighbor : infoMap.get(alerted).neighbors) {
            int neighborVersion = vertexVersion.get(neighbor) + 1;
            vertexVersion.put(neighbor, neighborVersion);
            pq.add(new Entry(neighbor, neighborVersion, computeScore(neighbor, infoMap, highAlertForts)));
        }
    }

    // This is the greedy score we use to guess which fort is best to hit next
    // Not perfect, but big gold = good and creating alerts = bad.
    private double computeScore(String candidate,
                                Map<String, FortInfo> infoMap,
                                Set<String> highAlertForts) {

        FortInfo info = infoMap.get(candidate);

        // A fort is on alert either because it's self alert or someone nearby triggered it
        boolean currentlyHighAlert = highAlertForts.contains(candidate) || info.selfAlert;
        double gold = info.value;

        // If it's on alert and not immune you only get half
        if (currentlyHighAlert && !info.immune) {
            gold /= 2.0;
        }

        // Shields are special, they don't alert neighbors,
        // so we give them a tiny bonus to pick them earlier in ties.
        if (info.shield) {
            return gold + info.neighbors.size() * 0.001;
        }

        // Estimate how much damage we'd cause by alerting neighbors
        double penalty = 0.0;
        for (String neighbor : info.neighbors) {
            FortInfo neighborInfo = infoMap.get(neighbor);

            // Skip neighbors that are already doomed or immune anyway
            if (highAlertForts.contains(neighbor) || neighborInfo.immune || neighborInfo.selfAlert) {
                continue;
            }

            // Rough guess, alerting them probably costs us half their gold
            penalty += neighborInfo.value / 2.0;
        }

        return gold - penalty;
    }

}
