import java.io.File;
import java.io.FileReader;
import java.util.Random;

import com.opencsv.CSVReader;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.stage.Stage;

public class Plotter extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        File[] predFiles = new File(System.getProperty("user.dir") + "/evaluation_results/Prediction").listFiles();
        File[] appFiles = new File(System.getProperty("user.dir") + "/evaluation_results/AppPerformance").listFiles();
        File[] poiFiles = new File(System.getProperty("user.dir") + "/evaluation_results/PoiPerformance").listFiles();

        // Pick 3 random files for the evaluation
        Random rand = new Random();
        int randomFileIndex;
        File predFile, appFile, poiFile;
        String predFilePath = "";
        String appFilePath = "";
        String poiFilePath = "";

        randomFileIndex = rand.nextInt(predFiles.length);
        for (File file : predFiles) {
            if (!predFiles[randomFileIndex].isHidden() && predFiles[randomFileIndex].isFile()) {
                predFilePath = predFiles[randomFileIndex].getPath();
                System.out.println(predFilePath);
                break;
            }
            else
                randomFileIndex = rand.nextInt(predFiles.length);
        }

        randomFileIndex = rand.nextInt(appFiles.length);
        for (File file : appFiles) {
            if (!appFiles[randomFileIndex].isHidden() && appFiles[randomFileIndex].isFile()) {
                appFilePath = appFiles[randomFileIndex].getPath();
                System.out.println(appFilePath);
                break;
            }
            else
                randomFileIndex = rand.nextInt(predFiles.length);
        }

        randomFileIndex = rand.nextInt(poiFiles.length);
        for (File file : poiFiles) {
            if (!poiFiles[randomFileIndex].isHidden() && poiFiles[randomFileIndex].isFile()) {
                poiFilePath = poiFiles[randomFileIndex].getPath();
                System.out.println(poiFilePath);
                break;
            }
            else
                randomFileIndex = rand.nextInt(poiFiles.length);
        }

        // Prediction evaluation
        stage.setTitle("n-MMC Evaluation: " + predFilePath.substring(61));
        final NumberAxis yAxis = new NumberAxis(); // auto-ranging
        final CategoryAxis xAxis = new CategoryAxis();

        final LineChart<String, Number> lineChart = new LineChart<>(xAxis, yAxis);
        yAxis.setLabel("Requests per Interval");
        xAxis.setLabel("Interval");
        lineChart.setTitle("Requests: Predicted V Real");

        XYChart.Series predicted = new XYChart.Series();
        XYChart.Series real = new XYChart.Series();

        predicted.setName("Predicted");
        real.setName("Real");

        try (CSVReader dataReader = new CSVReader(new FileReader(predFilePath))) {
            String[] nextLine;
            dataReader.skip(1); // Skip title
            while ((nextLine = dataReader.readNext()) != null) {
                String interval = nextLine[0];
                double predictedReqs = Double.parseDouble(nextLine[1]);
                int realReqs = Integer.parseInt(nextLine[2]);
                if (realReqs == 0) continue; // Ignore intervals of zero requests. TODO add a DC in created users
                predicted.getData().add(new XYChart.Data(interval, predictedReqs));
                real.getData().add(new XYChart.Data(interval, realReqs));
            }
        }

        lineChart.getData().addAll(predicted, real);
        Scene scene = new Scene(lineChart, 500, 400);
        stage.setScene(scene);
        stage.show();

        // App Performance evaluation (1)
        Stage stage2 = new Stage();
        stage2.setTitle("App Performance Evaluation (1/2): " + appFilePath.substring(65));
        final NumberAxis yAxis2 = new NumberAxis(); // auto-ranging
        final CategoryAxis xAxis2 = new CategoryAxis();

        final LineChart<String, Number> lineChart2 = new LineChart<>(xAxis2, yAxis2);
        yAxis2.setLabel("Requests per Interval");
        xAxis2.setLabel("Interval");
        lineChart2.setTitle("Requests: Admitted V Finished");

        XYChart.Series admitted = new XYChart.Series();
        XYChart.Series finished = new XYChart.Series();

        admitted.setName("Admitted");
        finished.setName("Finished");

        try (CSVReader dataReader = new CSVReader(new FileReader(appFilePath))) {
            String[] nextLine;
            dataReader.skip(1); // Skip title
            while ((nextLine = dataReader.readNext()) != null) {
                String interval = nextLine[0];
                int admittedReqs = Integer.parseInt(nextLine[1]);
                int finishedReqs = Integer.parseInt(nextLine[2]);
                admitted.getData().add(new XYChart.Data(interval, admittedReqs));
                finished.getData().add(new XYChart.Data(interval, finishedReqs));
            }
        }

        lineChart2.getData().addAll(admitted, finished);
        Scene scene2 = new Scene(lineChart2, 500, 400);
        stage2.setScene(scene2);
        stage2.show();

        // App Performance evaluation (2)
        Stage stage3 = new Stage();
        stage3.setTitle("App Performance Evaluation (2/2): " + appFilePath.substring(65));
        final NumberAxis yAxis3 = new NumberAxis(); // auto-ranging
        final CategoryAxis xAxis3 = new CategoryAxis();

        final LineChart<String, Number> lineChart3 = new LineChart<>(xAxis3, yAxis3);
        yAxis3.setLabel("Response Time (in seconds)");
        xAxis3.setLabel("Interval");
        lineChart3.setTitle("Response Time: Average V Reference");

        XYChart.Series avgResponseTime = new XYChart.Series();
        XYChart.Series refResponseTime = new XYChart.Series();

        avgResponseTime.setName("Average");
        refResponseTime.setName("Reference");

        try (CSVReader dataReader = new CSVReader(new FileReader(appFilePath))) {
            String[] nextLine;
            dataReader.skip(1); // Skip title
            while ((nextLine = dataReader.readNext()) != null) {
                String interval = nextLine[0];
                double art;
                if (Integer.parseInt(nextLine[2]) == 0) continue; // Skip if no finished requests in this interval exist
                else art = Double.parseDouble(nextLine[4]);
                int reft = 3; // TODO gain reference response time differently
                avgResponseTime.getData().add(new XYChart.Data(interval, art));
                refResponseTime.getData().add(new XYChart.Data(interval, reft));
            }
        }

        lineChart3.getData().addAll(avgResponseTime, refResponseTime);
        Scene scene3 = new Scene(lineChart3, 500, 400);
        stage3.setScene(scene3);
        stage3.show();

        // POI Performance evaluation (1)
        Stage stage4 = new Stage();
        stage4.setTitle("POI Performance Evaluation (1/2): " + poiFilePath.substring(65));
        final NumberAxis yAxis4 = new NumberAxis(); // auto-ranging
        final CategoryAxis xAxis4 = new CategoryAxis();

        final LineChart<String, Number> lineChart4 = new LineChart<>(xAxis4, yAxis4);
        yAxis4.setLabel("Power Consumption (in Watts)");
        xAxis4.setLabel("Interval");
        lineChart4.setTitle("Power Consumption");

        XYChart.Series powerConsumption = new XYChart.Series();

        powerConsumption.setName("Power Consumption");

        try (CSVReader dataReader = new CSVReader(new FileReader(poiFilePath))) {
            String[] nextLine;
            dataReader.skip(1); // Skip title
            while ((nextLine = dataReader.readNext()) != null) {
                String interval = nextLine[0];
                int pwrC = Integer.parseInt(nextLine[1]);
                powerConsumption.getData().add(new XYChart.Data(interval, pwrC));
            }
        }

        lineChart4.getData().addAll(powerConsumption);
        Scene scene4 = new Scene(lineChart4, 500, 400);
        stage4.setScene(scene4);
        stage4.show();

        // POI Performance evaluation (2)
        Stage stage5 = new Stage();
        stage5.setTitle("POI Performance Evaluation (2/2): " + poiFilePath.substring(65));
        final NumberAxis yAxis5 = new NumberAxis(); // auto-ranging
        final CategoryAxis xAxis5 = new CategoryAxis();

        final LineChart<String, Number> lineChart5 = new LineChart<>(xAxis5, yAxis5);
        yAxis5.setLabel("Cores");
        xAxis5.setLabel("Interval");
        lineChart5.setTitle("Allocated Cores");

        XYChart.Series allocatedCores = new XYChart.Series();

        allocatedCores.setName("Allocated Cores");

        try (CSVReader dataReader = new CSVReader(new FileReader(poiFilePath))) {
            String[] nextLine;
            dataReader.skip(1); // Skip title
            while ((nextLine = dataReader.readNext()) != null) {
                String interval = nextLine[0];
                int aloC = Integer.parseInt(nextLine[2]);
                allocatedCores.getData().add(new XYChart.Data(interval, aloC));
            }
        }

        lineChart5.getData().addAll(allocatedCores);
        Scene scene5 = new Scene(lineChart5, 500, 400);
        stage5.setScene(scene5);
        stage5.show();
    }

//    public static void main(String[] args) {
//        launch(args);
//    }

    public void doPlots() {
        launch();
    }
}