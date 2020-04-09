import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

public class MRF {

    private static final double A = 1;
    private static final double B = 1000;
    private static final double L = 1;
    private static final double k = 5;
    private static final double C0 = 2;
    private static final int SWEEPS = 1;
    private static final double X0_PERC = 0.6;

    private static int pois = 3;
    private static int apps = 2;
    private static int hops = 1;
    private static int[] residualResources = {1, 3, 2};
    private static int[][] residualWorkload = {{30, 5}, {0, 0}, {4, 7}};
    private static double[][] formationsWorkload = {{36.5, 27.8}, {150.78, 48.4}, {50, 50}, {78, 47.90}, {16.34, 91.09}};
    private static int[] powerConsumption = {3000, 6000, 4000, 4500, 4200}; // for each formation
    private static MRFNode[] nodes;

    public static void balanceLoadBetweenPois(int[][] residualWorkload, int[] residualResources) {
        for (int sweep = 0; sweep < SWEEPS; sweep ++) {
//            for (int poi = 0; poi < pois; poi++) {
        MRFNode mainNode = nodes[0]; // TODO iterate over nodes
        System.out.println("Checking Main Node with id: " + mainNode.id);
        ArrayList<int[][]> feasibleNeighborhoodStates = mainNode.calculateFeasibleNeighborhoodWorkloadStates();
        double[] neighborhoodStatesVc = new double[feasibleNeighborhoodStates.size()]; // goes hand in hand with the above array
        double[] neighborhoodStatesP = new double[feasibleNeighborhoodStates.size()]; // goes hand in hand with the above array
        double[] neighborhoodStatesCP = new double[feasibleNeighborhoodStates.size()]; // goes hand in hand with the above array

        int i = 0;
        // For every state of Xi (paired with the feasible states of Xj in the neighborhood)
        for (int[][] feasibleNeighborhoodState : feasibleNeighborhoodStates) {
            System.out.println("Checking Neighborhood State: " + Arrays.deepToString(feasibleNeighborhoodState));

            // ri
            int[] mainNodeWorkload = feasibleNeighborhoodState[0];

            // zi
            MRFNode.MRFNodeState state = mainNode.calculateResourceStateThatServesWorkload(mainNodeWorkload);
            System.out.println("Main Node Workload (ri): " + Arrays.toString(mainNodeWorkload));

            // sig (ri)
            double[] sigMainNodeWorkload = new double[mainNodeWorkload.length];
            for (int s = 0; s < mainNodeWorkload.length; s++)
                sigMainNodeWorkload[s] = revSigmoid(mainNodeWorkload[s], X0_PERC * state.getWorkload()[s]);
            System.out.println("Reverse Sigmoid of Main Node Workload (sig(ri)): " + Arrays.toString(sigMainNodeWorkload));

            // calculate V1 & V2
            double v1 = Integer.MAX_VALUE;
            double v2 = Integer.MAX_VALUE;
            if (state != null) {
                System.out.println("Selected Main Node Resource State: " + Arrays.toString(state.getResources()) + ", Max Workload: "
                        + Arrays.toString(state.getWorkload()) + ", Power Consumption: " + state.getPowerConsumption());

                System.out.println("Calculating V1...");
                v1 = A * state.getPowerConsumption() * Arrays.stream(sigMainNodeWorkload).sum();

                System.out.println("Calculating V2...");
                v2 = 0;
                for (int n = 1; n <= mainNode.neighbors.size(); n++) {
                    System.out.println("Checking Neighbor Node with id: " + mainNode.neighbors.get(n - 1).id);

                    // rj
                    int[] neighborNodeWorkload = feasibleNeighborhoodState[n];
                    System.out.println("Neighbor Node Workload (rj): " + Arrays.toString(neighborNodeWorkload));

                    // zj
                    MRFNode.MRFNodeState neighborState
                            = mainNode.neighbors.get(n - 1).calculateResourceStateThatServesWorkload(neighborNodeWorkload);

                    // sig (rj)
                    double[] sigNeighborNodeWorkload = new double[neighborNodeWorkload.length];
                    for (int s = 0; s < neighborNodeWorkload.length; s++)
                        sigNeighborNodeWorkload[s] = revSigmoid(neighborNodeWorkload[s], X0_PERC * neighborState.getWorkload()[s]);
                    System.out.println("Reverse Sigmoid of Neighbor Node Workload (sig(ri)): " + Arrays.toString(sigNeighborNodeWorkload));

                    // condition
                    if (neighborState != null) {
                        System.out.println("Selected Neighbor Node Resource State: " + Arrays.toString(neighborState.getResources()) + ", Max Workload: "
                                + Arrays.toString(neighborState.getWorkload()) + ", Power Consumption: " + neighborState.getPowerConsumption());
//                        v2 += B * dotProduct(mainNodeWorkload, neighborNodeWorkload);
                        v2 += B * dotProduct(sigMainNodeWorkload, sigNeighborNodeWorkload);
                    } else {
                        System.out.println("No matching Neighbor Node Resource State found!");
                        v2 = Integer.MAX_VALUE;
                        break;
                    }
                }
            } else {
                System.out.println("No matching Main Node Resource State found!");
            }
            System.out.println("V1: " + v1);
            System.out.println("V2: " + v2);
            System.out.println("Vc: " + (v1 + v2));
            // calculate Vc
            neighborhoodStatesVc[i] = Math.pow(Math.E, -1 * (v1 + v2) / temperature(sweep + 1));
            System.out.println("Temperature: " + temperature(sweep + 1));
            System.out.println("Ts(i): " + neighborhoodStatesVc[i]);
            System.out.println("Ts[array]: " + Arrays.toString(neighborhoodStatesVc));
            System.out.println("------------------------------------------------------------------------\n");
            i++;
        }
        System.out.println("------------------------------------------------------------------------\n");
        System.out.println("------------------------------------------------------------------------\n");
        // Caclulcate temperature sum
        System.out.println("Ts[array]: " + Arrays.toString(neighborhoodStatesVc));
        double ts = Arrays.stream(neighborhoodStatesVc).sum();
//        System.out.println("Ts (sum): " + ts);
        // Calculate state probabilities
        for (int s = 0; s < neighborhoodStatesVc.length; s++)
            neighborhoodStatesP[s] = neighborhoodStatesVc[s] / ts;
        System.out.println("Pi for each value of ri: " + Arrays.toString(neighborhoodStatesP));
        // Create state cumulative probabilities
        neighborhoodStatesCP[0] = neighborhoodStatesP[0];
        for (int s = 1; s < neighborhoodStatesP.length; s++)
            neighborhoodStatesCP[s] = neighborhoodStatesCP[s - 1] + neighborhoodStatesP[s];
        System.out.println("Cumulative Pi for each value of ri: " + Arrays.toString(neighborhoodStatesCP));
        // Select a new state for the neighborhood
        int nextStateIdx = 0;
        Random rand = new Random();
//        double p = rand.nextInt(neighborhoodStatesCP.length) / (1.0 * neighborhoodStatesCP.length);
        double p = rand.nextDouble();
        System.out.println("Random Seed: " + p);
        for (int s = 0; s < neighborhoodStatesCP.length; s++) {
            if (p <= neighborhoodStatesCP[s]) {
                nextStateIdx = s;
                break;
            }
        }
        System.out.println("Next State Index: " + nextStateIdx);
        // Update neighborhood states
        int[][] feasibleNeighborhoodState = feasibleNeighborhoodStates.get(nextStateIdx);
        System.out.println("...which corresponds to neighborhood state: " + Arrays.deepToString(feasibleNeighborhoodState));
        mainNode.updateState(feasibleNeighborhoodState[0]); // TODO: be carefull with neighbors!!!
        for (int n = 1; n <= mainNode.neighbors.size(); n++)
            mainNode.neighbors.get(n - 1).updateState(feasibleNeighborhoodState[n]);
//            }
        }
    }

    public static MRFNode[] createMRFGrid(int gridSIze) {
        MRFNode[] nodes = new MRFNode[gridSIze * gridSIze];
        int id = 0;

        for (int i = 0; i < gridSIze; i++) {
            for (int j = 0; j < gridSIze; j++) {
                nodes[id] = new MRFNode(id, new Coordinates(i,j), residualResources[id], residualWorkload[id]);
                id++;
                if (id == 3) return nodes;
            }
        }
//        createNeigborhoods(nodes, hops);
        return nodes;
    }

    public static void createNeigborhoods(MRFNode[] nodes, int hops) {
        for (int i = 0; i < nodes.length; i++) {
            MRFNode mainNode = nodes[i];
            for (int j = 0; j < nodes.length; j++) {
                if (i == j) continue;
                MRFNode candidateNeighbor = nodes[j];
                // Neighboring condition
                int xDist = Math.abs(candidateNeighbor.pos.x - mainNode.pos.x);
                int yDist = Math.abs(candidateNeighbor.pos.y - mainNode.pos.y);
                if ((xDist <= hops) && (yDist <= hops) && (xDist != yDist)) {
                    mainNode.addNeighbor(candidateNeighbor);
//                    System.out.println("Node with id: " + mainNode.id + " and position: " + mainNode.pos.x + "," +
//                            mainNode.pos.y + " has neighbor: " + candidateNeighbor.id);
                }
            }
        }
    }

    private static double revSigmoid(double x, double x0) {
        System.out.println("X0 = " + x0);
        return (L - (L / (1 + Math.pow(Math.E, (-1 * k * (x - x0))))));
    }

    private static double dotProduct(double[] vectA, double[] vectB) {
        double product = 0;
        int n = vectA.length;
        // Loop to calculate dot product
        for (int i = 0; i < n; i++)
            product += vectA[i] * vectB[i];
        return product;
    }

    private static double temperature(int w) {
        return (C0 / Math.log(1 + w));
    }

    public static void main(String[] args) {
        MRFNode.apps = apps;
        MRFNode.formationsPowerConsumption = powerConsumption;
        MRFNode.formationsWorkload = formationsWorkload;
        MRFNode.relaxationFactor = 5;
//        balanceLoadBetweenPois(residualWorkload, residualResources);
        nodes = createMRFGrid(3);
//        createNeigborhoods(nodes, 1);

//        for (int[] rw : residualWorkload) {
//            nodes[0].calculateResourceStateThatServesWorkload(rw);
//            nodes[1].calculateResourceStateThatServesWorkload(rw);
//            nodes[2].calculateResourceStateThatServesWorkload(rw);
//        }

        nodes[0].addNeighbor(nodes[1]);
        nodes[0].addNeighbor(nodes[2]);
//        nodes[0].calculateFeasibleNeighborhoodWorkloadStates();
        balanceLoadBetweenPois(residualWorkload, residualResources);
    }
}
