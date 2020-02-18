import lpsolve.LpSolve;
import lpsolve.LpSolveException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

// "Static" Class
public final class Optimizer {

    private Optimizer () { // private constructor
    }

    // Use energy criteria
    private static ArrayList<Integer> optimizeVmPlacement (double[][] vmGuaranteedRR, int hosts,
                                                           ArrayList<int[]> combinations, double[] predictedWorkload)
            throws LpSolveException {

        ArrayList<Integer> result = new ArrayList<>();
        LpSolve lp;
        int nCol, k, ret = 0;

        int n = combinations.size();
        // create a model with 0 rows and n columns
        nCol = n; // n variables

        int[] colno = new int[nCol];
        double[] row = new double[nCol];

        lp = LpSolve.makeLp(0, nCol);
        if (lp.getLp() == 0)
            ret = 1;

        if (ret == 0) {
            // name variables and make them integer
            for (int i = 1; i <= n; i++) {
                lp.setColName(i, "p" + i);
                lp.setInt(i, true);
            }

            /* makes building the model faster if it is done rows by row */
            lp.setAddRowmode(true);

            /* construct 1st constraint (Σ pi λ1 >= Λ1) */
            for (k = 0; k < n; k++) {
                colno[k] = k + 1;
                row[k] = vmGuaranteedRR[0][combinations.get(k)[0]];
                /* add the row to lpsolve */
            }
            lp.addConstraintex(k, row, colno, LpSolve.GE, predictedWorkload[0]);

            /* construct 2nd constraint (Σ pi λ2 >= Λ2) */
            for (k = 0; k < n; k++) {
                colno[k] = k + 1;
                row[k] = vmGuaranteedRR[1][combinations.get(k)[1]];
                /* add the row to lpsolve */
            }
            lp.addConstraintex(k, row, colno, LpSolve.GE, predictedWorkload[1]);

            /* construct 3rd constraint, part A (pi >= 0) */
            for (k = 0; k < n; k++) {
                colno[k] = k + 1;
                row[k] = 1;
                /* add the row to lpsolve */
                lp.addConstraintex(k + 1, row, colno, LpSolve.GE, 0);
                Arrays.fill(row, 0);
                Arrays.fill(colno, 0);
            }


            /* construct 3rd constraint, part B (pi <= 3) */
            for (k = 0; k < n; k++) {
                colno[k] = k + 1;
                row[k] = 1;
                /* add the row to lpsolve */
                lp.addConstraintex(k + 1, row, colno, LpSolve.LE, 3);
                Arrays.fill(row, 0);
                Arrays.fill(colno, 0);
            }

            /* construct 4th constraint (Σ pi <= Hosts */
            for (k = 0; k < n; k++) {
                colno[k] = k + 1;
                row[k] = 1;
                /* add the row to lpsolve */
            }
            lp.addConstraintex(k, row, colno, LpSolve.LE, hosts);

            //////////// OBJECTIVE FUNCTION /////////////
            lp.setAddRowmode(false); /* rowmode should be turned off again when done building the model */
            for (k = 0; k < n; k++) {
                colno[k] = k + 1;
                row[k] = 1;
                /* add the row to lpsolve */
            }
            lp.setObjFnex(k, row, colno);

            /* set the object direction to minimize */
            lp.setMinim();

            /* just out of curiosity, now generate the model in lp format in file model.lp */
//            lp.writeLp("model.lp");

            /* I only want to see important messages on screen while solving */
            lp.setVerbose(LpSolve.IMPORTANT);
            /* Now let lpsolve calculate a solution */
            ret = lp.solve();
            if (ret == LpSolve.OPTIMAL)
                ret = 0;
            else
                ret = 5;
        }

        if (ret == 0) {
            /* a solution is calculated, now lets get some results */

            /* objective value */
//            System.out.println("Objective value: " + lp.getObjective());

            /* variable values */
            lp.getVariables(row);
            for (k = 0; k < nCol; k++) {
//                System.out.println(lp.getColName(k + 1) + ": " + row[k]);
                for (int v = (int) row[k]; v > 0; v--)
                    result.add(k);
            }
            //if result size < HOSTS then add zeros padding
            while (result.size() < hosts) result.add(0);
//            System.out.println(Arrays.toString(result.toArray()));
            /* we are done now */
        } else {
            System.out.println("\n\t#-------------------------------------------- Optimizer failed; random combs");
            while (result.size() < hosts) result.add(ThreadLocalRandom.current().nextInt(0, n));
        }

        /* clean up such that all used memory by lpsolve is freed */
        if (lp.getLp() != 0)
            lp.deleteLp();

//        return(ret);
        return result;

    }
}