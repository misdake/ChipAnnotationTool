package com.rs.tool.chipannotation;

import com.rs.tool.chipannotation.log.Log;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.InputStream;
import java.net.URL;

public class App extends Application {

    public static void main(String[] args) {
        Log.gson.toJson(null);
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
            Scene scene = new Scene(root);
            primaryStage.setScene(scene);

            InputStream imageInput = getClass().getClassLoader().getResourceAsStream("icon.jpg");
            assert imageInput != null;
            Image image = new Image(imageInput);
            this.primaryStage.getIcons().add(image);

            primaryStage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
