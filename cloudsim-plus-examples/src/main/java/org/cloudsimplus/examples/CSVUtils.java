package org.cloudsimplus.examples;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class CSVUtils {

    private static final char DEFAULT_SEPARATOR = ',';

    public static void writeLine(Writer w, List<String> values) throws IOException {
        writeLine(w, values, DEFAULT_SEPARATOR, ' ');
    }

    public static void writeLine(Writer w, List<String> values, char separators) throws IOException {
        writeLine(w, values, separators, ' ');
    }

    //https://tools.ietf.org/html/rfc4180
    private static String followCVSformat(String value) {

        String result = value;
        if (result.contains("\"")) {
            result = result.replace("\"", "\"\"");
        }
        return result;

    }

    public static void writeLine(Writer w, List<String> values, char separators, char customQuote) throws IOException {

        boolean first = true;

        //default customQuote is empty

        if (separators == ' ') {
            separators = DEFAULT_SEPARATOR;
        }

        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (!first) {
                sb.append(separators);
            }
            if (customQuote == ' ') {
                sb.append(followCVSformat(value));
            } else {
                sb.append(customQuote).append(followCVSformat(value)).append(customQuote);
            }
            first = false;
        }
        sb.append("\n");
        w.append(sb.toString());


    }

    //if "firstLine" true, remove first line containing headers
    public static double[][] readTo2dArray(String fileName, boolean firstLine) {
        File file = new File(fileName);
        double[][] results = new double[0][0];
        int rows = 0, cols = 0, i, j;

        // this gives you a 2-dimensional array of strings
        List<List<String>> lines = new ArrayList<>();
        Scanner inputStream;

        try {

            //scan
            inputStream = new Scanner(file);
            while(inputStream.hasNext()){
                String line= inputStream.next();
                if (firstLine) {
                    firstLine = false;
                    continue;
                }
                String[] values = line.split(",");
                cols = values.length;
                rows++;
            }

            i = 0;
            firstLine = true;
            results = new double[rows][cols];
            inputStream = new Scanner(file);

            //fill array
            while(inputStream.hasNext()){
                String line= inputStream.next();
                if (firstLine) {
                    firstLine = false;
                    continue;
                }
                j = 0;
                String[] values = line.split(",");
                for (String value: values) {
                    results[i][j] = Double.parseDouble(value);
                    j++;
                }
                i++;
            }

            inputStream.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return results;
    }
}
