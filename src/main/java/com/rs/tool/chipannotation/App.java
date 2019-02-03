package com.rs.tool.chipannotation;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

public class App extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        initRootLayout();
    }

    public void initRootLayout() {
        try {
            URL resource = getClass().getClassLoader().getResource("ImageProcessor.fxml");
            assert resource != null;
            Parent root = FXMLLoader.load(resource);

            primaryStage.setResizable(false);
            primaryStage.setTitle("ChipAnnotationTool - Image Processor");
            Scene scene = new Scene(root, 400, 280);
            primaryStage.setScene(scene);
            primaryStage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
