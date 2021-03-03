import com.google.common.primitives.Ints;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.IntStream;

public class MRFNode {
    public static int relaxationFactor;
    public static int apps;
    public static double[][] formationsWorkload;
    public static int[] formationsPowerConsumption;

    public int id;
    public Coordinates pos;
    public ArrayList<MRFNode> neighbors;
    public int[] currentWorkload;
    public int[] initialWorkload;
    public MRFNodeState currentResourcesState;

    private int residualResources;
    private int noOfFormations;
    private MRFNodeState[] states;

    public MRFNode(int id, Coordinates pos, int residualResources, int[] residualWorkload) {
        this.id = id;
        this.pos = pos;
        this.residualResources = residualResources;
        this.noOfFormations = formationsWorkload.length;

        this.neighbors = new ArrayList<>();

        ArrayList<int[]> nodePossibleResourcesStates = calculateNodePossibleResourceStates();
        ArrayList<double[]> nodePossibleWorkloadStates = calculateNodePossibleWorkloadStates(nodePossibleResourcesStates);
        ArrayList<Integer> nodePossiblePowerConsStates = calculateNodePossiblePowerConsStates(nodePossibleResourcesStates);
        int noOfPossibleStates = nodePossibleResourcesStates.size();

        this.states = new MRFNodeState[noOfPossibleStates];

//        System.out.println("Before Sorting: ");
        for (int i = 0; i < noOfPossibleStates; i++) {
            this.states[i] = new MRFNodeState(i, nodePossibleResourcesStates.get(i), nodePossibleWorkloadStates.get(i),
                    nodePossiblePowerConsStates.get(i));
//            System.out.println("State ID: " + this.states[i].id + ", Power Consumption: " + this.states[i].powerCons);
        }

        // parallel sort states from less to most energy consuming one
        Arrays.sort(this.states, new MRFNodeStatePowerConsComparator());

//        System.out.println("After Sorting: ");
//        for (int i = 0; i < noOfPossibleStates; i++) {
//            System.out.println("State ID: " + this.states[i].id + ", Power Consumption: " + this.states[i].powerCons);
//        }

        // this.currentResourcesState = this.states[0]; // initialize poi state with all servers closed
        // this.currentWorkload = residualWorkload; // set current workload as the residual
        this.updateState(residualWorkload); // set current workload as the residual
        this.initialWorkload = residualWorkload;
    }

    public int relaxResidualWorkload(int residualWorkload) {
        int relaxedRW = residualWorkload;

        for (int i = 0; i < relaxationFactor; i++) {
            if (!(relaxedRW % relaxationFactor == 0))
                relaxedRW += 1;
        }

        return relaxedRW;
    }

    public void addNeighbor(MRFNode n) {
        this.neighbors.add(n);
    }

    public ArrayList<int[]> calculateNodePossibleResourceStates() {
        // create all possible states
        ArrayList<int[]> poiStates = new ArrayList();
        int[] values = new int[this.residualResources + 1];
        for (int b = 0; b <= this.residualResources; b++) {
            values[b] = b;
        }
//        System.out.println("All possible values for state of POI " + id + ": " + Arrays.toString(values));

        for (int[] permutation : Permutator.calculatePermutationsOfLength(values, this.noOfFormations)) {
            // First Check
            if (IntStream.of(permutation).sum() <= this.residualResources) poiStates.add(permutation);
        }

//        System.out.println("All possible Resource states for POI " + id + ": ");
//        poiStates.forEach(state -> System.out.println(Arrays.toString(state)));
//        System.out.println("------");

        return poiStates;
    }

    public ArrayList<double[]> calculateNodePossibleWorkloadStates(ArrayList<int[]> nodePossibleResourcesStates) {
        ArrayList<double[]> workloadStates = new ArrayList();
//        System.out.println("Workload of all formations: " + Arrays.deepToString(formationsWorkload));
        for (int[] resourcesState: nodePossibleResourcesStates) {
//            System.out.println("For this resource state: " + Arrays.toString(resourcesState));
            double[] workloadState = new double[this.apps];
            for (int i = 0; i < resourcesState.length; i++) {
                for (int app = 0; app < this.apps; app++) {
                    workloadState[app] += resourcesState[i] * formationsWorkload[i][app];
                }
            }
//            System.out.println("The corresponding workload state: " + Arrays.toString(workloadState));
//            System.out.println();
            workloadStates.add(workloadState);
        }

//        System.out.println("All possible Workload states for POI " + id + ": ");
//        workloadStates.forEach(state -> System.out.println(Arrays.toString(state)));

        return workloadStates;
    }

    public ArrayList<Integer> calculateNodePossiblePowerConsStates(ArrayList<int[]> nodePossibleResourcesStates) {
        ArrayList<Integer> powerConsStates = new ArrayList();
//        System.out.println("Power Consumption of all formations: " + Arrays.toString(formationsPowerConsumption));
        for (int[] resourcesState: nodePossibleResourcesStates) {
//            System.out.println("For this resource state: " + Arrays.toString(resourcesState));
            int powerConsState = 0;
            for (int i = 0; i < resourcesState.length; i++) {
                powerConsState += resourcesState[i] * formationsPowerConsumption[i];
            }
//            System.out.println("The corresponding power consumption: " + powerConsState);
//            System.out.println();
            powerConsStates.add(powerConsState);
        }

//        System.out.println("All possible Power Consumption states for POI " + id + ": ");
//        powerConsStates.forEach(state -> System.out.println(state));

        return powerConsStates;
    }

    public void updateState(int[] currentWorkload) {
        this.currentWorkload = currentWorkload;
        this.currentResourcesState = calculateResourceStateThatServesWorkload(this.currentWorkload);
    }

    // Returns the most energy efficient poi state that serves the current workload
    public MRFNodeState calculateResourceStateThatServesWorkload(int[] residualWorkload) {
//        System.out.println("Checking for POI: " + this.id);
//        System.out.println("Workload to satisfy: " + Arrays.toString(residualWorkload));
        for (MRFNodeState state: this.states) {
            boolean fits = true;
            for (int app = 0; app < apps; app++) {
                if (residualWorkload[app] > state.workload[app]) {
                    fits = false;
                    break;
                }
            }
            if (fits) {
//                System.out.println("State with workload capacity : " + state.workload[0] + ", " + state.workload[1] + " fits");
//                System.out.println("State Power Consumption : " + state.powerCons + "\n");
                return state;
            }
        }
//        System.out.println("No state can satisfy given workload!\n");
        return null; // return zero resources state if workload cannot be satisfied // TODO reconsider
    }

    // Calculates the possible interactions and subsequently assigned workloads of in the nodes neighborhood
    public ArrayList<int[][]> calculateFeasibleNeighborhoodWorkloadStates() {
        ArrayList<int[][]> neighborhoodWorkloadStates = new ArrayList<>();

//        System.out.println("Neighborhood of PoI: " + this.id);

        // Create all possible values of each app workload for node
        ArrayList<Integer> valuesSuperset = new ArrayList<>();
        int[] totalNeighborhoodWorkload = new int[apps];
        for (int app = 0; app < apps; app++) {
//            System.out.println("App: " + app);
            totalNeighborhoodWorkload[app] = this.currentWorkload[app];
//            System.out.println("Main Node Adding: " + this.currentWorkload[app]);
            for (MRFNode node : this.neighbors) {
                totalNeighborhoodWorkload[app] += node.currentWorkload[app];
//                System.out.println("Adding: " + node.currentWorkload[app]);
            }
//            System.out.println("Total neighborhood workload (that has to remain intact) of App " + app + ": " + totalNeighborhoodWorkload[app]);
            totalNeighborhoodWorkload[app] = relaxResidualWorkload(totalNeighborhoodWorkload[app]);
//            System.out.println("Total neighborhood workload (that has to remain intact) of App " + app + ", after relaxation: " + totalNeighborhoodWorkload[app]);

            ArrayList<Integer> values = new ArrayList<>();
            for (int b = 0; b < totalNeighborhoodWorkload[app] + 1; b++) {
                if (b % relaxationFactor == 0) values.add(b);
            }
//            System.out.println("All possible values of App: " + app + " for state of Neighborhood: " + values);
            if (values.size() > valuesSuperset.size()) valuesSuperset = values;
        }

        ArrayList<int[]> appPairs = new ArrayList<>();
//        System.out.println("Values Superset: " + valuesSuperset);
//        System.out.println("Apps: " + apps);
        // Pair app workload values
        for (int[] permutation : Permutator.calculatePermutationsOfLength(Ints.toArray(valuesSuperset), apps)) {
            // First Check
//            System.out.println("Checking permutation: " + Arrays.toString(permutation));
            boolean fits = true;
            for (int app = 0; app < apps; app++) {
                if (permutation[app] > totalNeighborhoodWorkload[app]) fits = false;
//                System.out.println("Checking permutation: " + Arrays.toString(permutation));
            }
            if (fits) appPairs.add(permutation);
        }

//        System.out.println("All possible appPairs for Neighborhood: ");
//        appPairs.forEach(state -> System.out.println(Arrays.toString(state)));

        int size = 0;
//        System.out.println("Neighborhood size: " + (this.neighbors.size() + 1));
        for (int[][] permutation : Permutator.calculatePermutationsOfLength(appPairs, this.neighbors.size() + 1)) {
            size++;
//            System.out.println("Checking: " + Arrays.deepToString(permutation));
            boolean fits = true;
            for (int app = 0; app < apps; app++) {
                int workloadAppSum = 0;
                for (int permIdx = 0; permIdx < permutation.length; permIdx++) {
                    workloadAppSum += permutation[permIdx][app];
                }
                if (!(workloadAppSum == totalNeighborhoodWorkload[app])) {
                    fits = false;
                    break;
                }
            }
            if (fits) {
                neighborhoodWorkloadStates.add(permutation);
//                System.out.println(Arrays.deepToString(permutation));
            }
        }

//        System.out.println("State - Space size (prior to pruning): " + size);
//        System.out.println("All possible states for Neighborhood: ");
//        neighborhoodWorkloadStates.forEach(state -> System.out.println(Arrays.deepToString(state)));

//        System.out.println("State - Space size: " + neighborhoodWorkloadStates.size());

        return neighborhoodWorkloadStates;
    }

    public class MRFNodeState {

        private int[] resources;
        private double[] workload;
        private int powerCons;
        private int id;

        private MRFNodeState(int stateId, int[] resources, double[] workload, int powerCons) {
            this.id = stateId;
            this.resources = resources;
            this.workload = workload;
            this.powerCons = powerCons;
        }

        public int getPowerConsumption(){
            return this.powerCons;
        }

        public int[] getResources(){
            return this.resources;
        }

        public double[] getWorkload() {
            return this.workload;
        }
    }

    private class MRFNodeStatePowerConsComparator implements Comparator<MRFNodeState> {
        @Override
        public int compare(MRFNodeState n1, MRFNodeState n2) {
            return Integer.compare(n1.powerCons, n2.powerCons);
        }
    }
}
