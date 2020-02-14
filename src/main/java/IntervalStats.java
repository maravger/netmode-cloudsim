public class IntervalStats {
    private final int[][] intervalFinishedTasks;
    private final int[][] intervalAdmittedTasks;
    private final double[][] accumulatedResponseTime;

    public IntervalStats(int[][] intervalFinishedTasks, int[][] intervalAdmittedTasks, double[][] accumulatedResponseTime) {
        this.intervalFinishedTasks = intervalFinishedTasks;
        this.intervalAdmittedTasks = intervalAdmittedTasks;
        this.accumulatedResponseTime = accumulatedResponseTime;
    }

    public int[][] getIntervalFinishedTasks() {
        return intervalFinishedTasks;
    }

    public int[][] getIntervalAdmittedTasks() {
        return intervalAdmittedTasks;
    }

    public double[][] getAccumulatedResponseTime() {
        return accumulatedResponseTime;
    }
}
