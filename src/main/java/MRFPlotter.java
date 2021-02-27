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

public class MRFPlotter extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        File mrfFile = new File(System.getProperty("user.dir") + "/evaluation_results/MRF").listFiles()[0];
        String mrfFilePath = mrfFile.getPath();

        // Prediction evaluation
        stage.setTitle("MRF Evaluation: " + mrfFilePath.substring(61));
        final NumberAxis yAxis = new NumberAxis(); // auto-ranging
        final CategoryAxis xAxis = new CategoryAxis();

        final LineChart<String, Number> lineChart = new LineChart<>(xAxis, yAxis);
        yAxis.setLabel("Vc Score");
        xAxis.setLabel("Sweep");
        lineChart.setTitle("MRF: Vc per Sweep");

        XYChart.Series vc = new XYChart.Series();
        XYChart.Series sweep = new XYChart.Series();

        vc.setName("Vc");

        try (CSVReader dataReader = new CSVReader(new FileReader(mrfFilePath))) {
            String[] nextLine;
            dataReader.skip(1); // Skip title
            while ((nextLine = dataReader.readNext()) != null) {
                String sweeps = nextLine[0];
                double vcScore = Double.parseDouble(nextLine[1]);
                vc.getData().add(new XYChart.Data(sweeps, vcScore));
            }
        }

        lineChart.getData().addAll(vc);
        Scene scene = new Scene(lineChart, 500, 400);
        stage.setScene(scene);
        stage.show();
    }

//    public static void main(String[] args) {
//        launch(args);
//    }

    public void doPlots() {
        launch();
    }
}