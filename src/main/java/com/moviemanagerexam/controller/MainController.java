package com.moviemanagerexam.controller;

import com.moviemanagerexam.dao.CategoryDAO;
import com.moviemanagerexam.dao.DatabaseConnection;
import com.moviemanagerexam.dao.MovieCategoryDAO;
import com.moviemanagerexam.dao.MovieDAO;
import com.moviemanagerexam.model.Category;
import com.moviemanagerexam.model.Movie;
import com.moviemanagerexam.util.AlertHelper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class MainController {

    @FXML private TableView<Movie> tableView;
    @FXML private TableColumn<Movie, String> titleColumn;
    @FXML private TableColumn<Movie, Double> imdbRatingColumn;
    @FXML private TableColumn<Movie, Integer> personalRatingColumn;
    @FXML private TableColumn<Movie, List<String>> categoryColumn;

    @FXML private TextField searchField;
    @FXML private MenuButton categoryFilterMenu;
    @FXML private Spinner<Double> minRatingSpinner;
    @FXML private TextField imdbLinkField;
    @FXML private Button addByLinkButton;

    @FXML private Button addButton;
    @FXML private Button deleteButton;
    @FXML private Button editButton;
    @FXML private Button editRatingButton;
    @FXML private Button playButton;

    private MovieDAO movieDAO;
    private CategoryDAO categoryDAO;
    private MovieCategoryDAO movieCategoryDAO;

    private ObservableList<Movie> allMovies;

    @FXML
    public void initialize() {
        movieDAO = new MovieDAO();
        categoryDAO = new CategoryDAO();
        movieCategoryDAO = new MovieCategoryDAO();

        allMovies = FXCollections.observableArrayList();

        titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        imdbRatingColumn.setCellValueFactory(new PropertyValueFactory<>("imdbRating"));
        personalRatingColumn.setCellValueFactory(new PropertyValueFactory<>("personalRating"));
        categoryColumn.setCellValueFactory(cellData -> {
            List<String> categories = cellData.getValue().getCategories();
            return new javafx.beans.property.SimpleObjectProperty<>(categories);
        });
        categoryColumn.setCellFactory(column -> new TableCell<Movie, List<String>>() {
            @Override
            protected void updateItem(List<String> categories, boolean empty) {
                super.updateItem(categories, empty);
                if (empty || categories == null || categories.isEmpty()) {
                    setText("");
                } else {
                    setText(String.join(", ", categories));
                }
            }
        });

        SpinnerValueFactory<Double> valueFactory = new SpinnerValueFactory.DoubleSpinnerValueFactory(0.0, 10.0, 0.0, 0.5);
        minRatingSpinner.setValueFactory(valueFactory);
        minRatingSpinner.setEditable(true);

        Task<Void> loadDataTask = new Task<Void>() {
            @Override
            protected Void call() {
                try {
                    DatabaseConnection.initializeDatabase();
                    loadMovies();
                    loadCategories();
                    Platform.runLater(MainController.this::checkMoviesForDeletion);
                } catch (Exception e) {
                    Platform.runLater(() -> AlertHelper.showError("Database Error", "Failed to load data from database: " + e.getMessage()));
                }
                return null;
            }
        };

        setupEventHandlers();
        new Thread(loadDataTask).start();
    }

    private void checkMoviesForDeletion() {
        java.time.LocalDateTime twoYearsAgo = java.time.LocalDateTime.now().minusYears(2);
        List<Movie> toDelete = allMovies.stream()
                .filter(m -> m.getPersonalRating() < 6)
                .filter(m -> {
                    if (m.getLastView() == null || m.getLastView().isEmpty()) return true;
                    try {
                        java.time.LocalDateTime lastViewDate = java.time.LocalDateTime.parse(m.getLastView().replace(" ", "T"));
                        return lastViewDate.isBefore(twoYearsAgo);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .collect(Collectors.toList());

        if (!toDelete.isEmpty()) {
            StringBuilder sb = new StringBuilder("The following movies have a rating below 6 and haven't been opened for 2 years:\n");
            toDelete.forEach(m -> sb.append("- ").append(m.getTitle()).append("\n"));
            sb.append("\nPlease consider deleting them.");
            AlertHelper.showWarning("Suggested Deletions", sb.toString());
        }
    }

    private void setupEventHandlers() {
        searchField.textProperty().addListener((observable, oldValue, newValue) -> filterMovies());
        minRatingSpinner.valueProperty().addListener((observable, oldValue, newValue) -> filterMovies());

        playButton.setOnAction(event -> playSelectedMovie());
        addButton.setOnAction(event -> addMovie());
        addByLinkButton.setOnAction(event -> addMovieByLink());
        editButton.setOnAction(event -> editMovie());
        editRatingButton.setOnAction(event -> editRating());
        deleteButton.setOnAction(event -> deleteMovie());
    }

    private void loadMovies() {
        try {
            List<Movie> movies = movieDAO.getAllMovies();
            Platform.runLater(() -> {
                allMovies.setAll(movies);
                tableView.setItems(allMovies);
            });
        } catch (SQLException e) {
            Platform.runLater(() -> AlertHelper.showError("Database Error", "Failed to load movies: " + e.getMessage()));
        }
    }

    private void loadCategories() {
        try {
            List<Category> categories = categoryDAO.getAllCategories();
            Platform.runLater(() -> {
                categoryFilterMenu.getItems().clear();
                for (Category category : categories) {
                    CheckMenuItem item = new CheckMenuItem(category.name());
                    item.setUserData(category);
                    item.selectedProperty().addListener((obs, old, val) -> filterMovies());
                    categoryFilterMenu.getItems().add(item);
                }
            });
        } catch (Exception e) {
            Platform.runLater(() -> AlertHelper.showError("Database Error", "Failed to load categories: " + e.getMessage()));
        }
    }

    private void filterMovies() {
        String searchText = searchField.getText().toLowerCase();
        double minRating = minRatingSpinner.getValue();
        ObservableList<Movie> filteredMovies = FXCollections.observableArrayList();

        for (Movie movie : allMovies) {
            boolean matchesSearch = movie.getTitle().toLowerCase().contains(searchText);
            boolean matchesRating = movie.getImdbRating() >= minRating;
            boolean matchesCategory = true;

            List<CheckMenuItem> selectedCategories = categoryFilterMenu.getItems().stream()
                    .filter(item -> item instanceof CheckMenuItem)
                    .map(item -> (CheckMenuItem) item)
                    .filter(CheckMenuItem::isSelected)
                    .collect(Collectors.toList());

            if (!selectedCategories.isEmpty()) {
                matchesCategory = selectedCategories.stream()
                        .anyMatch(item -> {
                            Category cat = (Category) item.getUserData();
                            return movie.getCategories().contains(cat.name());
                        });
            }

            if (matchesSearch && matchesRating && matchesCategory) {
                filteredMovies.add(movie);
            }
        }

        tableView.setItems(filteredMovies);
    }

    private void playSelectedMovie() {
        Movie selectedMovie = tableView.getSelectionModel().getSelectedItem();
        if (selectedMovie == null) {
            AlertHelper.showWarning("No selection", "Please select a movie to play.");
            return;
        }

        File movieFile = new File(selectedMovie.getFileLink());
        if (!movieFile.exists()) {
            AlertHelper.showError("File Error", "Movie file not found: " + selectedMovie.getFileLink());
            return;
        }

        try {
            movieDAO.updateLastView(selectedMovie.getId());
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(movieFile);
            }
        } catch (Exception e) {
            AlertHelper.showError("Error", "Failed to open movie: " + e.getMessage());
        }
    }

    private void addMovieByLink() {
        String imdbUrl = imdbLinkField.getText().trim();
        if (imdbUrl.isEmpty()) {
            AlertHelper.showWarning("Empty Link", "Please enter an IMDb link.");
            return;
        }

        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Select Movie File");
        fileChooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("Movie Files", "*.mp4", "*.mpeg4", "*.mkv", "*.avi", "*.mov")
        );
        File selectedFile = fileChooser.showOpenDialog(addByLinkButton.getScene().getWindow());

        if (selectedFile == null) {
            AlertHelper.showWarning("No File", "Please select a movie file.");
            return;
        }

        new Thread(() -> {
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(imdbUrl))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                String html = response.body();

                String title = "Unknown";
                double rating = 0.0;
                List<String> genres = new ArrayList<>();

                Pattern jsonLdPattern = Pattern.compile("<script type=\"application/ld\\+json\">(.*?)</script>", Pattern.DOTALL);
                Matcher jsonLdMatcher = jsonLdPattern.matcher(html);
                
                if (jsonLdMatcher.find()) {
                    String jsonLd = jsonLdMatcher.group(1);
                    
                    Pattern titlePattern = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
                    Matcher titleMatcher = titlePattern.matcher(jsonLd);
                    if (titleMatcher.find()) {
                        title = titleMatcher.group(1).trim();
                    }
                    
                    Pattern aggregatePattern = Pattern.compile("\"aggregateRating\"\\s*:\\s*\\{([^}]+)\\}", Pattern.DOTALL);
                    Matcher aggregateMatcher = aggregatePattern.matcher(jsonLd);
                    if (aggregateMatcher.find()) {
                        String aggregateRating = aggregateMatcher.group(1);
                        Pattern ratingPattern = Pattern.compile("\"ratingValue\"\\s*:\\s*\"?([\\d.]+)\"?");
                        Matcher ratingMatcher = ratingPattern.matcher(aggregateRating);
                        if (ratingMatcher.find()) {
                            try {
                                rating = Double.parseDouble(ratingMatcher.group(1));
                            } catch (NumberFormatException e) {
                                rating = 0.0;
                            }
                        }
                    }
                    
                    Pattern genrePattern = Pattern.compile("\"genre\"\\s*:\\s*\\[(.*?)\\]");
                    Matcher genreMatcher = genrePattern.matcher(jsonLd);
                    if (genreMatcher.find()) {
                        String genreString = genreMatcher.group(1);
                        Pattern singleGenrePattern = Pattern.compile("\"([^\"]+)\"");
                        Matcher singleGenreMatcher = singleGenrePattern.matcher(genreString);
                        while (singleGenreMatcher.find()) {
                            genres.add(singleGenreMatcher.group(1));
                        }
                    }
                }

                String finalTitle = title;
                double finalRating = rating;
                List<String> finalGenres = genres;
                javafx.application.Platform.runLater(() -> {
                    try {
                        Movie movie = new Movie(finalTitle, finalRating, 0, selectedFile.getAbsolutePath());
                        movieDAO.addMovie(movie);
                        
                        if (!finalGenres.isEmpty()) {
                            for (String genre : finalGenres) {
                                try {
                                    List<Category> allCategories = categoryDAO.getAllCategories();
                                    for (Category cat : allCategories) {
                                        if (cat.name().equalsIgnoreCase(genre)) {
                                            movieCategoryDAO.addMovieToCategory(movie.getId(), cat.id());
                                            break;
                                        }
                                    }
                                } catch (SQLException e) {
                                    System.err.println("Failed to link genre " + genre + ": " + e.getMessage());
                                }
                            }
                        }
                        
                        loadMovies();
                        imdbLinkField.clear();
                        String genreInfo = finalGenres.isEmpty() ? "" : " [" + String.join(", ", finalGenres) + "]";
                        AlertHelper.showInfo("Success", "Added '" + finalTitle + "' with rating " + finalRating + genreInfo);
                    } catch (SQLException e) {
                        AlertHelper.showError("Database Error", "Failed to save movie: " + e.getMessage());
                    }
                });

            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> 
                    AlertHelper.showError("Scraping Error", "Failed to get info from IMDb: " + e.getMessage()));
            }
        }).start();
    }

    private void addMovie() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Select Movie File");
        fileChooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("Movie Files", "*.mp4", "*.mpeg4", "*.mkv", "*.avi", "*.mov")
        );
        File selectedFile = fileChooser.showOpenDialog(addButton.getScene().getWindow());

        if (selectedFile != null) {
            TextInputDialog titleDialog = new TextInputDialog(selectedFile.getName());
            titleDialog.setTitle("Add Movie");
            titleDialog.setHeaderText("Enter movie title");
            titleDialog.showAndWait().ifPresent(title -> {
                TextInputDialog ratingDialog = new TextInputDialog("0");
                ratingDialog.setTitle("Add Rating");
                ratingDialog.setHeaderText("Enter personal rating (0-10)");
                ratingDialog.showAndWait().ifPresent(ratingStr -> {
                    try {
                        int rating = Integer.parseInt(ratingStr);
                        Movie movie = new Movie(title, 0.0, rating, selectedFile.getAbsolutePath());
                        movieDAO.addMovie(movie);
                        loadMovies();
                    } catch (Exception e) {
                        AlertHelper.showError("Error", "Invalid rating or database error: " + e.getMessage());
                    }
                });
            });
        }
    }

    private void editMovie() {
        Movie selectedMovie = tableView.getSelectionModel().getSelectedItem();
        if (selectedMovie == null) {
            AlertHelper.showWarning("No selection", "Please select a movie to edit.");
            return;
        }

        TextInputDialog titleDialog = new TextInputDialog(selectedMovie.getTitle());
        titleDialog.setTitle("Edit Movie");
        titleDialog.setHeaderText("Update movie title");
        titleDialog.showAndWait().ifPresent(newTitle -> {
            try {
                selectedMovie.setTitle(newTitle);
                movieDAO.updateMovie(selectedMovie);
                
                TextInputDialog ratingDialog = new TextInputDialog(String.valueOf(selectedMovie.getPersonalRating()));
                ratingDialog.setTitle("Edit Rating");
                ratingDialog.setHeaderText("Update personal rating (0-10)");
                ratingDialog.showAndWait().ifPresent(newRatingStr -> {
                    try {
                        int newRating = Integer.parseInt(newRatingStr);
                        selectedMovie.setPersonalRating(newRating);
                        movieDAO.updateMovie(selectedMovie);
                    } catch (Exception e) {
                    }
                });

                manageCategories(selectedMovie);
                loadMovies();
            } catch (SQLException e) {
                AlertHelper.showError("Database Error", "Failed to update movie: " + e.getMessage());
            }
        });
    }

    private void editRating() {
        Movie selectedMovie = tableView.getSelectionModel().getSelectedItem();
        if (selectedMovie == null) {
            AlertHelper.showWarning("No selection", "Please select a movie to edit the rating.");
            return;
        }

        TextInputDialog ratingDialog = new TextInputDialog(String.valueOf(selectedMovie.getPersonalRating()));
        ratingDialog.setTitle("Edit Rating");
        ratingDialog.setHeaderText("Update personal rating for " + selectedMovie.getTitle());
        ratingDialog.setContentText("Rating (0-10):");
        ratingDialog.showAndWait().ifPresent(newRatingStr -> {
            try {
                int newRating = Integer.parseInt(newRatingStr);
                if (newRating < 0 || newRating > 10) {
                    AlertHelper.showWarning("Invalid Rating", "Please enter a rating between 0 and 10.");
                    return;
                }
                selectedMovie.setPersonalRating(newRating);
                movieDAO.updateMovie(selectedMovie);
                loadMovies();
                AlertHelper.showInfo("Success", "Rating updated successfully.");
            } catch (NumberFormatException e) {
                AlertHelper.showWarning("Invalid Input", "Please enter a valid number.");
            } catch (SQLException e) {
                AlertHelper.showError("Database Error", "Failed to update rating: " + e.getMessage());
            }
        });
    }

    private void manageCategories(Movie movie) {
        try {
            List<Category> allCats = categoryDAO.getAllCategories();
            ChoiceDialog<Category> catDialog = new ChoiceDialog<>(null, allCats);
            catDialog.setTitle("Manage Categories");
            catDialog.setHeaderText("Add a category to '" + movie.getTitle() + "'");
            catDialog.showAndWait().ifPresent(cat -> {
                try {
                    movieCategoryDAO.addMovieToCategory(movie.getId(), cat.id());
                } catch (SQLException e) {
                    AlertHelper.showError("Database Error", "Failed to link category: " + e.getMessage());
                }
            });
        } catch (SQLException e) {
            AlertHelper.showError("Database Error", "Failed to load categories: " + e.getMessage());
        }
    }

    private void deleteMovie() {
        Movie selectedMovie = tableView.getSelectionModel().getSelectedItem();
        if (selectedMovie == null) {
            AlertHelper.showWarning("No selection", "Please select a movie to delete.");
            return;
        }

        boolean confirmed = AlertHelper.showConfirmation(
                "Delete Movie",
                "Are you sure you want to delete the movie: " + selectedMovie.getTitle() + "?"
        );
        if (confirmed) {
            try {
                movieDAO.deleteMovie(selectedMovie.getId());
                loadMovies();
                AlertHelper.showInfo("Movie Deleted", "The movie has been deleted successfully.");
            } catch (SQLException e) {
                AlertHelper.showError("Delete Error", "Failed to delete movie: " + e.getMessage());
            }
        }
    }
}
