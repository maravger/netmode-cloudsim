import java.util.ArrayList;

// "Static" Class
public class Permutator {

    private Permutator () { // private constructor
    }

    public static ArrayList<int[]> calculatePermutationsOfLength(int[] flavorCores, int length) {
        ArrayList<int[]> permutations = new ArrayList<>();
        int size = flavorCores.length;

        // There can be (len)^l permutations
        for (int i = 0; i < (int)Math.pow(size, length); i++) {
            // Convert i to len th base
            permutations.add(createPermutation(i, flavorCores, size, length));
        }

        return permutations;
    }

    // Overloading
    public static ArrayList<int[][]> calculatePermutationsOfLength(ArrayList<int[]> formations, int length) {
        ArrayList<int[][]> permutations = new ArrayList<>();
        int size = formations.size();

        // There can be (len)^l permutations
        for (int i = 0; i < (int)Math.pow(size, length); i++) {
            // Convert i to len th base
            permutations.add(createPermutation(i, formations, size, length));
        }

        return permutations;
    }

    private static int[] createPermutation(int n, int arr[], int len, int L) {
        int[] permutation = new int[L];
        // Sequence is of length L
        for (int i = 0; i < L; i++) {
            // Print the ith element of sequence
//            System.out.print(arr[n % len]);
            permutation[i] = arr[n % len];
            n /= len;
        }
//        System.out.print();
//        System.out.println(Arrays.toString(permutation));

        return permutation;
    }

    // Overloading
    private static int[][] createPermutation(int n, ArrayList<int[]> arr, int len, int L) {
        int[][] permutation = new int[L][];

        // Sequence is of length L
        for (int i = 0; i < L; i++) {
            // Print the ith element of sequence
//            System.out.print(arr[n % len]);
            permutation[i] = arr.get(n % len);
            n /= len;
        }
//        System.out.println(Arrays.deepToString(permutation));

        return permutation;
    }
}
