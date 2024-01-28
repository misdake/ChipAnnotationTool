package com.rs.tool.chipannotation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.paint.Paint;
import javafx.stage.FileChooser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UiController implements Initializable {

    public Button buttonSelectDataFolder;
    public Button buttonSelectImageFile;
    public Button buttonStart;
    public ProgressBar progressProcess;

    public TextField textDataFolder;
    public TextField textImageFileName;

    public TextField textVendor;
    public TextField textType;
    public TextField textFamily;
    public TextField textName;

    public TextField textSourceUrl;
    public TextField textGithubRepo;
    public TextField textGithubIssueId;
    public TextField textPhysicalWidth;
    public TextField textPhysicalHeight;

    public Label labelStatus;

    private File dataFolder = null;
    private File imageFile = null;

    private void reportError(String error) {
        Platform.runLater(() -> {
            labelStatus.setTextFill(Paint.valueOf("#8b0000"));
            labelStatus.setText(error);
        });
    }

    private void reportInfo(String info) {
        Platform.runLater(() -> {
            labelStatus.setTextFill(Paint.valueOf("#000"));
            labelStatus.setText(info);
        });
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
    }

    public void onSelectFolderEvent(ActionEvent actionEvent) {
        String listFolder = AppConfig.instance().getListFolder();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Data List");
        fileChooser.setInitialDirectory(new File(listFolder != null ? listFolder : System.getProperty("user.home")));
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Data List", "list.json"));
        File listFile = fileChooser.showOpenDialog(((Node) actionEvent.getTarget()).getScene().getWindow());
        if (listFile != null) {
            File folder = listFile.getParentFile();
            this.dataFolder = folder;
            textDataFolder.setText(folder.getAbsolutePath());
            AppConfig.instance().setListFolder(folder.getAbsolutePath());
        }
    }

    public void onOpenImageEvent(ActionEvent actionEvent) {
        String imageFolder = AppConfig.instance().getImageFolder();
        File imageFolderFile = new File(imageFolder);
        if (!imageFolderFile.exists()) {
            imageFolder = null;
        }
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Image File");
        fileChooser.setInitialDirectory(new File(imageFolder != null ? imageFolder : System.getProperty("user.home")));
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Image File", "*.png", "*.jpg"));
        File imageFile = fileChooser.showOpenDialog(((Node) actionEvent.getTarget()).getScene().getWindow());
        if (imageFile != null) {
            this.imageFile = imageFile;
            textImageFileName.setText(imageFile.getAbsolutePath());
            AppConfig.instance().setImageFolder(imageFile.getParentFile().getAbsolutePath());
            String name = imageFile.getName();
            name = name.substring(0, name.lastIndexOf('.'));

            textName.setText(name);

            if (name.contains("CPU")) textType.setText("CPU");
            if (name.contains("GPU")) textType.setText("GPU");
            if (name.contains("SOC")) textType.setText("SOC");

            if (name.contains("AMD")) textVendor.setText("AMD");
            if (name.contains("Intel")) textVendor.setText("Intel");
            if (name.contains("nVIDIA")) textVendor.setText("nVIDIA");

            //reset textfields
            reportInfo("");
            setProgress(0, 1);
            textSourceUrl.setText("");
            textGithubIssueId.setText("");
            textPhysicalWidth.setText("");
            textPhysicalHeight.setText("");
        }
    }

    private void setProgress(int current, int total) {
        double progress = 1.0 * current / total;
        this.progressProcess.setProgress(progress);
    }

    public void onStartEvent(ActionEvent actionEvent) {
        es.submit(() -> {


            reportInfo("started");

            if (this.dataFolder == null) {
                reportError("Data List is not selected");
                return;
            }
            if (this.imageFile == null) {
                reportError("Image File is not selected");
                return;
            }
            if (this.textName.getText().trim().isEmpty()) {
                reportError("Image name is not specified");
                return;
            }

            double[] size = new double[]{0, 0};
            int[] issueId = new int[]{-1};
            try {
                issueId[0] = Integer.parseInt(this.textGithubIssueId.getText());
                size[0] = Double.parseDouble(this.textPhysicalWidth.getText());
                size[1] = Double.parseDouble(this.textPhysicalHeight.getText());
            } catch (Exception ignored) {
            }
            if (size[0] == 0 || size[1] == 0) {
                size[0] = size[1] = 0;
            }
            if (issueId[0] == -1) {
                reportError("please enter correct issue id");
                return;
            }


            reportInfo("reading image");

            ImageContent imageContent = Cut.readImage(this.imageFile);

            if (imageContent == null) {
                reportError("cannot read image file");
                return;
            }

            imageContent.vendor = textVendor.getText().trim();
            imageContent.type = textType.getText().trim();
            imageContent.family = textFamily.getText().trim();
            imageContent.name = textName.getText().trim();

            imageContent.widthMillimeter = size[0];
            imageContent.heightMillimeter = size[1];
            imageContent.githubRepo = textGithubRepo.getText();
            imageContent.githubIssueId = issueId[0];
            imageContent.source = textSourceUrl.getText();
            imageContent.createTime = new Date();

            if (imageContent.name.length() == 0) {
                reportError("name cannot be empty");
                return;
            }
            int split = imageContent.githubRepo.indexOf('/');
            if (split < 0) {
                reportError("githubRepo should be 'username/reponame'");
                return;
            }
            String username = imageContent.githubRepo.substring(0, split);
            String reponame = imageContent.githubRepo.substring(split + 1);
            String githubPagesRoot = String.format("https://%s.github.io/%s/%s", username, reponame, imageContent.name);

            int accu = 0;
            for (ImageContent.Level level : imageContent.levels) {
                accu += level.xMax * level.yMax;
            }
            int total = accu;

            Cut.cutAll(imageContent, this.imageFile, dataFolder, new Cut.ProgressCallback() {
                int current = 0;

                @Override
                public void doneCut(int level, int x, int y) {
                    current++;
                    setProgress(current, total);
                }

                @Override
                public void alldone() {

                }

                @Override
                public void show(String message) {
                    reportInfo(message);
                }
                @Override
                public void failed(String reason) {
                    reportError(reason);
                }

            });

            reportInfo("saving content.json");
            String json = gson.toJson(imageContent);
            try {
                Files.write(new File(dataFolder.getAbsolutePath() + "/" + imageContent.name + "/content.json").toPath(), json.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                e.printStackTrace();
                reportError("cannot write content.json");
            }

            reportInfo("saving chip list");
            ChipList chipList = new ChipList(dataFolder.getAbsolutePath() + "/list.json");
            if (!chipList.load()) {
                reportError("cannot read list.json");
                return;
            }
            chipList.add(new ChipList.Chip(imageContent.vendor, imageContent.type, imageContent.family, imageContent.name, githubPagesRoot));
            if (!chipList.save()) {
                reportError("cannot write list.json");
                return;
            }

            reportInfo("all done");
        });
    }

    private static final ExecutorService es = Executors.newCachedThreadPool();
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

}
