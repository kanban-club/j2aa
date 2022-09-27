package club.kanban.j2aaconverter;

public class CSVFormatter {
    static String formatQuotes(String s){
        return "\"" + s.replace("\"", "'") + "\"";
    }

    static String formatCommas(String s){
        return s.replace(",", " ");
    }
}
