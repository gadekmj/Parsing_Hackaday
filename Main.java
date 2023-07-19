import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import javax.swing.*;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.SocketTimeoutException;

public class Main {
    public static void main(String[] args)
    {
        SwingApp app = new SwingApp();
        app.createAndShowGUI();
    }

    public static class SwingApp extends JFrame implements ActionListener {
        // JComboBox dla sortowania
        String[] sortOptions = {
                "Most likes",
                "Newest",
                "Most viewed",
                "Most commented",
                "Most followers",
                "Recently updated",
                "skulls"
        };
        private static final Logger logger = LogManager.getLogger(SwingApp.class); //deklaracja logera                            !!!!!!!!

        private JTextField projectsField;
        private JTextField keywordField;
        private JTextArea resultArea;

        private JComboBox<String> sortComboBox;
        public void createAndShowGUI() {
            logger.info("Application started");

            setTitle("Hackaday Project Scraper");

            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            JPanel inputPanel = new JPanel(); // Panel wejściowy

            // Pole "Number of projects"
            JLabel projectsLabel = new JLabel("Number of projects:");
            projectsLabel.setFont(new Font(Font.DIALOG, Font.PLAIN, 12)); // Ustawienie rozmiaru czcionki
            inputPanel.add(projectsLabel);
            projectsField = new JTextField(10);
            inputPanel.add(projectsField);

            // Pole "Title keyword"
            JLabel keywordLabel = new JLabel("Title keyword (optional):");
            keywordLabel.setFont(new Font(Font.DIALOG, Font.PLAIN, 12)); // Ustawienie rozmiaru czcionki
            inputPanel.add(keywordLabel);
            keywordField = new JTextField(10);
            inputPanel.add(keywordField);

            // Pole "Sort by"
            JLabel sortLabel = new JLabel("Sort by:");
            sortLabel.setFont(new Font(Font.DIALOG, Font.PLAIN, 12)); // Ustawienie rozmiaru czcionki
            inputPanel.add(sortLabel);
            sortComboBox = new JComboBox<>(sortOptions);
            inputPanel.add(sortComboBox);

            // Przycisk "Scrape Projects"
            JButton scrapeButton = new JButton("Scrape Projects");
            scrapeButton.addActionListener(this);
            inputPanel.add(scrapeButton);

            // Obszar tekstowy
            resultArea = new JTextArea(30, 60);
            resultArea.setEditable(false);
            JScrollPane scrollPane = new JScrollPane(resultArea);

            // Główne rozmieszczenie
            setLayout(new BorderLayout(5, 5)); // Ustawienie odstępów między komponentami
            add(inputPanel, BorderLayout.NORTH);
            add(scrollPane, BorderLayout.CENTER);

            pack(); // Dopasowanie rozmiaru okna
            setLocationRelativeTo(null); // Wyśrodkowanie okna na ekranie
            setVisible(true);
        }

        @Override
        public void actionPerformed(ActionEvent e)
        {
            logger.info("Button clicked");     //użycie loggera

            resultArea.setText("");
            try {
                int n = Integer.parseInt(projectsField.getText());
                if (n <= 0) {
                    resultArea.append("Invalid value of 'n'. Please enter a positive natural number.\n");
                    return;
                }
            } catch (NumberFormatException i) {
                resultArea.append("Invalid value of 'n'. Please enter a valid integer.\n");
                return;
            }
            int n = Integer.parseInt(projectsField.getText());
            String titleKeyword = keywordField.getText(); //filtrowanie/słowo kluczowe
            String baseUrl = "https://hackaday.io/projects"; //adres strony
            String sortParam = (String) sortComboBox.getSelectedItem(); // Odczytanie wybranego parametru sortowania

            scrapeAllProjects(n, titleKeyword, baseUrl, changeName(sortParam));
        }

        private void scrapeAllProjects(int n, String titleKeyword, String baseUrl, String sortParam) {
            int count = 0; // zmienna licząca liczbe wyswietlnonych stron
            int counter = 1;
            String nextUrl = baseUrl +"?sort="+ sortParam;

            try {
                while (count < n) {
                    try {
                        //Document doc = Jsoup.connect(nextUrl).get(); //pobranie html
                        Document doc = connectWithRetries(nextUrl, 3, counter); // Pobieranie danych z ponownymi próbami
                        Elements projectItems = doc.select("div.project-item"); //pobranie z htlm projektów

                        if (projectItems.isEmpty()) {
                            throw new Exception_different_html(); //w przypadku zmiany htmpl będzie 0
                        }

                        for (Element projectItem : projectItems) //wykonuje się do momentu braku projektów na stronie
                        {
                            if (count >= n) break;

                            String desc = projectItem.select("div.project-item-title").text(); //pobranie opisu
                            String title = projectItem.select("div.project-item-cover ").attr("title"); //pobranie tytułu

                            if (titleKeyword.isEmpty() || title.toLowerCase().contains(titleKeyword.toLowerCase()) || desc.toLowerCase().contains(titleKeyword.toLowerCase())) //sprawdzenie
                            {
                                String projectUrl = projectItem.select("a.item-link").attr("href"); //pobranie adresu

                                Font boldFont = new Font(resultArea.getFont().getName(), Font.BOLD, 12);
                                resultArea.setFont(boldFont);

     /* logger     */           logger.info(counter + ": Title: {}", title);

                                resultArea.append(counter +" Title: " + title + "\n");    //wypisanie tytułu w gui
                                resultArea.append("Link to project: https://hackaday.io" + projectUrl + "\n\n"); //wypisanie adresu
                                resultArea.update(resultArea.getGraphics()); ///
                                counter++;
                                count++;
                            }
                        }

                        if (count >= n) break;

                        nextUrl = "https://hackaday.io" + getNextPageUrl(doc) ;//+"&sort="+ sortParam; // przejscie na kolejną stronę
                        //resultArea.append(nextUrl+"\n"); sprawdzenie
                    } catch (SocketTimeoutException e)
                    {
                        logger.warn("Socket timeout exception occurred, retrying...");
                    } catch (IOException e) {
                        e.printStackTrace();
                        break;
                    }
                }
                if (count == 0) {
                    resultArea.append("No projects matching the criteria found.");
                }
            } catch (Exception_different_html e) {
                resultArea.append("ERROR\nThe HTML of the page was changed");
            }
        }
        private Document connectWithRetries(String url, int maxRetries, int counter) throws IOException
        {
            int retries = 0; // Licznik prób
            while (retries < maxRetries) {
                try {
                    return Jsoup.connect(url).get();
                } catch (SocketTimeoutException e) {
                    retries++; // Zwiększenie licznika prób
                    logger.warn("Socket timeout exception occurred, retrying... (attempt {}/{})", retries, maxRetries);
                }
            }
            resultArea.append("Because of network problems you were able to get  " + counter + " Titles"+ "\n");
            throw new IOException("Maximum number of retries reached");
        }

        private String getNextPageUrl(Document doc) //pobranie adresu kolejnej strony
        {
            Element nextButton = doc.select("a.next-button").first();
            return nextButton != null ? nextButton.attr("href") : null;
        }
        private String changeName(String sortParam1)
        {
            String sortParam="";
            switch (sortParam1) {
                case "Most likes":
                    sortParam = "skulls";
                    break;
                case "Newest":
                    sortParam = "date";
                    break;
                case "Most viewed":
                    sortParam = "views";
                    break;
                case "Most commented":
                    sortParam = "comments";
                    break;
                case "Most followers":
                    sortParam = "followers";
                    break;
                case "Recently updated":
                    sortParam = "updated";
                    break;
                default:
                    break;

            }
            return sortParam;
        }





    }

}
