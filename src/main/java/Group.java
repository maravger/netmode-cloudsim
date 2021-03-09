import org.apache.commons.lang3.ArrayUtils;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
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
    private Integer[] assignedUserSequence;
    private int move;
    private int real_size;
    private Integer[][] mobData;

    private static final boolean USE_REAL_DATASET = true;
    private static final String REAL_DATASET_FILE = "/Users/avgr_m/Downloads/mobility_dataset.csv";
    private static final int TTL = 9; // time to live

    public Group(int id, int size, int app, Coordinates currPos, int gridSize, int nmmcHistory) {
        this.howManyVisited = 1;
        this.currPos = currPos;
        this.alreadyAccessed = new boolean[gridSize][gridSize];
        this.gridSize = gridSize;
        this.size = size;
        this.real_size = size;
        this.app = app;
        this.id = id;
        this.historicState = "";
        this.nmmcHistory = nmmcHistory;

        if (USE_REAL_DATASET) {
            mobData = readMobCSVData();
            Random rand = new Random();

            int userID = rand.nextInt(mobData.length);
            this.assignedUserSequence = mobData[userID];
            this.move = 1;
            // System.out.println("Assigned User Sequence: " + Arrays.toString(this.assignedUserSequence));

            int tile = ArrayUtils.indexOf(this.assignedUserSequence, this.move);
            if (tile == -1) {
                this.size = 0; // not sending
                tile = 0;
            }
            // int tile = this.indexOfSmallest(this.assignedUserSequence);
            // System.out.println("Starting Tile: " + tile);
            this.currPos = new Coordinates(tile / gridSize, tile % gridSize);
            // System.out.println("Corresponds to starting position: " + this.currPos.x + ", " + this.currPos.y);
        }
    }

    public void move(HashMap<String, int[]> transitionsLog, int poi) {
        if (USE_REAL_DATASET) {
            if (this.move == TTL) { // assign a new user
                // System.out.println("Group reached end of life. Assigning new user.");
                Random rand = new Random();
                int userID = rand.nextInt(mobData.length);
                this.assignedUserSequence = mobData[userID];
                // System.out.println("Assigned User Sequence: " + Arrays.toString(this.assignedUserSequence));
                this.move = 0;
                this.size = 0;
                // pressAnyKeyToContinue();
            }

            this.move++;
            int next_tile = ArrayUtils.indexOf(this.assignedUserSequence, move);
            if (next_tile == -1) {
                // System.out.println("Not moving...");
                this.size = 0;
                next_tile = 0;
                this.currPos = new Coordinates(next_tile / gridSize, next_tile % gridSize);
                // System.out.println("Current Position : " + this.currPos.x + ", " + this.currPos.y + " and size: " +
                //         this.size);
                // pressAnyKeyToContinue();
                return;
            }
            else {
                if (this.size > 0) { // meaning if previously the user was in a legit position
                    // System.out.println("The user previously was in a legit position. Moving (and logging)...");
                    // System.out.println("Assigned User Sequence Reminder: " + Arrays.toString(this.assignedUserSequence));
                    this.nextPos = new Coordinates(next_tile / gridSize, next_tile % gridSize);
                    // System.out.println("Current Position : " + this.currPos.x + ", " + this.currPos.y + " and size: " +
                    //         this.size);
                    // System.out.println("Next Position : " + this.nextPos.x + ", " + this.nextPos.y);
                    logTransitions(this.gridSize * this.currPos.x + this.currPos.y,
                            this.gridSize * this.nextPos.x + this.nextPos.y, transitionsLog, poi);
                    this.updatePosition();
                }
                else {
                    this.size = this.real_size;
                    // System.out.println("The user previously was NOT in a legit position. Still Moving (without logging)...");
                    // System.out.println("Assigned User Sequence Reminder: " + Arrays.toString(this.assignedUserSequence));
                    this.nextPos = new Coordinates(next_tile / gridSize, next_tile % gridSize);
                    // System.out.println("Current Position : " + this.currPos.x + ", " + this.currPos.y + " and size: " +
                    //         this.size);
                    // System.out.println("Next Position : " + this.nextPos.x + ", " + this.nextPos.y);
                    this.updatePosition();
                }
            }
        }
        else {
            this.updateAlreadyAccessed();

            // Choose next position
            ArrayList<ArrayList<Double>> grid = new ArrayList(this.gridSize);
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
                    } else {
                        newVal = Math.round(largestDist / tile * 100.0) / 100.0;
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
                            // System.out.println(totalVal);
                            // System.out.println(p);
                            // System.out.println(cumulativeProbability);
                            // System.out.println(tile);
                            // System.out.println("-----------");
                            // System.out.println(x);
                            // System.out.println(y);
                            this.nextPos = new Coordinates(x, y);
                            selectedNextState = true;
                            break outerLoop;
                        }
                    }
                }
            }
            // System.out.println("----------- GROUP: " + this.id + " -----------");
            // System.out.println("Already Accessed: ");
            // for (boolean[] line : this.alreadyAccessed) {
            //     for (boolean tile : line) {
            //         if (tile)
            //             System.out.print(1 + " ");
            //         else
            //             System.out.print(0 + " ");
            //     }
            //     System.out.println();
            // }

            logTransitions(this.gridSize * this.currPos.x + this.currPos.y,
                    this.gridSize * this.nextPos.x + this.nextPos.y, transitionsLog, poi);

            this.updatePosition();

            // System.out.println("----------- GROUP POSITION -----------");
            // for (int x = 0; x < this.gridSize; x++) {
            //     for (int y = 0; y < this.gridSize; y++) {
            //         if ((this.currPos.x == x) && (this.currPos.y == y))
            //             System.out.print("X ");
            //         else
            //             System.out.print("0 ");
            //     }
            //     System.out.println();
            // }
        }
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

    public Integer[][] readMobCSVData() {
        ArrayList<ArrayList<Integer>> mobTempData = new ArrayList<>();
        BufferedReader csvReader = null;

        try {
            csvReader = new BufferedReader(new FileReader(REAL_DATASET_FILE));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        // skip header line
        try {
            csvReader.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }

        while (true) {
            String row = "";
            try {
                if (!((row = csvReader.readLine()) != null)) break;
            } catch (IOException e) {
                e.printStackTrace();
            }
            String[] str_data = row.split(",");
            int size = str_data.length;
            ArrayList<Integer> dbl_data = new ArrayList<>();
            // Convert to doubles
            for(int i = 0; i < size; i++) {
                dbl_data.add(Integer.parseInt(str_data[i]));
            }
            // System.out.println(Arrays.toString(dbl_data.toArray()));
            mobTempData.add(dbl_data);
        }
        try {
            csvReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Convert Arraylist of Arraylists to 2d Array one-liner
        Integer[][] mobArrayData = mobTempData.stream().map(u -> u.toArray(new Integer[0])).toArray(Integer[][]::new);
        // System.out.println(Arrays.deepToString(mobArrayData));

        return mobArrayData;
    }

    private static void pressAnyKeyToContinue()
    {
        System.out.println("Press Enter key to continue...");
        try
        {
            System.in.read();
        }
        catch(Exception e)
        {}
    }

    private void logTransitions(int prevState, int nextState, HashMap<String, int[]> transitionsLog, int poi) {
        if (this.historicState.length() > this.nmmcHistory) this.historicState = this.historicState.substring(1); // remove oldest state
        this.historicState += Integer.toString(prevState); //concat previous state
        if (!transitionsLog.containsKey(this.historicState)) {
            transitionsLog.put(this.historicState, new int[poi]); // create the array
        }
        // System.out.println("Previous State: " + prevState);
        // System.out.println("Historic State: " + historicState);
        // System.out.println("Next State: " + nextState);
        transitionsLog.get(this.historicState)[nextState]++;
        // System.out.println("Transition Log: ");
        transitionsLog.entrySet().forEach(entry -> {
            System.out.println(entry.getKey() + " -> " + Arrays.toString(entry.getValue()));
        });
    }
}
