import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

public class Group {
    public boolean[][] alreadyAccessed; // 1 = been there, 0 = not been there
    public int howManyVisited;
    public int gridSize;
    public Coordinates currPos;
    public Coordinates nextPos;
    public int size;
    public int app;
    public int id;
    public String historicState;
    public int nmmcHistory;

    public Group(int id, int size, int app, Coordinates currPos, int gridSize, int nmmcHistory) {
        this.howManyVisited = 1;
        this.currPos = currPos;
        this.alreadyAccessed = new boolean[gridSize][gridSize];
        this.gridSize = gridSize;
        this.size = size;
        this.app = app;
        this.id = id;
        this.historicState = "";
        this.nmmcHistory = nmmcHistory;
    }

    public void move(HashMap<String, int[]> transitionsLog, int poi) {
        this.updateAlreadyAccessed();

        // Choose next position
        ArrayList<ArrayList<Double> > grid = new ArrayList(this.gridSize);
        double largestDist = 0;
        for (int x = 0; x < this.gridSize; x++) {
            ArrayList<Double> line = new ArrayList<>();
            for (int y = 0; y < this.gridSize; y++) {
                double dist = Math.abs(this.currPos.x - x) + Math.abs(this.currPos.y - y);
                line.add(dist);
                if (dist > largestDist) {
                    largestDist = dist;
                }
            }
            grid.add(line);
        }

        double newVal;
        double totalVal = 0; // this will be used to form the cumulative distribution function
        for (int x = 0; x < this.gridSize; x++) {
            for (int y = 0; y < this.gridSize; y++) {
                double tile = grid.get(x).get(y);
                if (tile == 0) {
                    newVal = 0;
                }
                else {
                    newVal = Math.round(largestDist/tile * 100.0) / 100.0;
                }
                grid.get(x).set(y, newVal);
                totalVal += newVal;
            }
        }

        // Pick next step with the help of a Gaussian Probability distribution
        Random rand = new Random();
        int p;
        double cumulativeProbability;
        Boolean selectedNextState = false;
        while (!selectedNextState) {
            p = rand.nextInt((int) totalVal);
            cumulativeProbability = 0;
            outerLoop:
            for (int x = 0; x < this.gridSize; x++) {
                for (int y = 0; y < this.gridSize; y++) {
                    cumulativeProbability += grid.get(x).get(y);
                    if (p <= cumulativeProbability && !this.alreadyAccessed[x][y]) {
//                        System.out.println(totalVal);
//                        System.out.println(p);
//                        System.out.println(cumulativeProbability);
//                        System.out.println(tile);
//                        System.out.println("-----------");
//                        System.out.println(x);
//                        System.out.println(y);
                        this.nextPos = new Coordinates(x, y);
                        selectedNextState = true;
                        break outerLoop;
                    }
                }
            }
        }
//        System.out.println("----------- GROUP: " + this.id + " -----------");
//        System.out.println("Already Accessed: ");
//        for (boolean[] line : this.alreadyAccessed) {
//            for (boolean tile : line) {
//                if (tile)
//                    System.out.print(1 + " ");
//                else
//                    System.out.print(0 + " ");
//            }
//            System.out.println();
//        }

        logTransitions(this.gridSize * this.currPos.x + this.currPos.y,
                this.gridSize * this.nextPos.x + this.nextPos.y, transitionsLog, poi);

        this.updatePosition();

//        System.out.println("----------- GROUP POSITION -----------");
//        for (int x = 0; x < this.gridSize; x++) {
//            for (int y = 0; y < this.gridSize; y++) {
//                if ((this.currPos.x == x) && (this.currPos.y == y))
//                    System.out.print("X ");
//                else
//                    System.out.print("0 ");
//            }
//            System.out.println();
//        }
    }

    private void updateAlreadyAccessed() {
        this.alreadyAccessed[currPos.x][currPos.y] = true;
    }

    private void updatePosition() {
        this.currPos = this.nextPos;
        this.howManyVisited++;

        // Check if group has finished the tour
        if (this.howManyVisited == this.gridSize * this.gridSize) {
//            System.out.println("!!! Tour Finished");
            this.alreadyAccessed = new boolean[this.gridSize][this.gridSize];
            this.howManyVisited = 1;
        }
    }

    private void logTransitions(int prevState, int nextState, HashMap<String, int[]> transitionsLog, int poi) {
        if (this.historicState.length() > this.nmmcHistory) this.historicState = this.historicState.substring(1); // remove oldest state
        this.historicState += Integer.toString(prevState); //concat previous state
        if (!transitionsLog.containsKey(this.historicState)) {
            transitionsLog.put(this.historicState, new int[poi]); // create the array
        }
//        System.out.println("Previous State: " + prevState);
//        System.out.println("Historic State: " + historicState);
//        System.out.println("Next State: " + nextState);
        transitionsLog.get(this.historicState)[nextState]++;
//        System.out.println("Transition Log: ");
//        transitionsLog.entrySet().forEach(entry -> {
//            System.out.println(entry.getKey() + " -> " + Arrays.toString(entry.getValue()));
//        });
    }
}
