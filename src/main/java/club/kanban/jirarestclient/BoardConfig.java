package club.kanban.jirarestclient;

import lombok.Getter;
import net.rcarz.javaclient.agile.AgileResource;
import net.rcarz.jiraclient.Field;
import net.rcarz.jiraclient.JiraException;
import net.rcarz.jiraclient.RestClient;
import net.sf.json.JSONObject;

import java.util.List;

public class BoardConfig extends AgileResource {
    @Getter
    private String type;
    @Getter
    private List<BoardColumn> boardColumns;

    public BoardConfig(RestClient restClient, JSONObject json) throws JiraException {
        super(restClient, json);
    }

    @Override
    protected void deserialize(JSONObject json) throws JiraException {
        super.deserialize(json);
        type = Field.getString(json.get("type"));

        JSONObject columnConfig = (JSONObject) json.get("columnConfig");
        boardColumns = getResourceArray(BoardColumn.class, columnConfig, getRestclient(), "columns");
        for (int i = 0; i < boardColumns.size(); i++)
            boardColumns.get(i).setId(/*12345 +*/ (long) i); //Generate virtual id for each column
    }
}
