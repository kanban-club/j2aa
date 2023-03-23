package club.kanban.j2aa.jirarestclient;

import lombok.Getter;
import net.rcarz.jiraclient.JiraException;
import net.rcarz.jiraclient.RestClient;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

public class JiraResource {
    public static final String RESOURCE_URI = "rest/agile/latest/";
    @Getter
    private final RestClient restClient;
    @Getter
    private long id = 0;
    @Getter
    private String name;
    @Getter
    private String self;
    private final JSONObject attributes = new JSONObject();

    /**
     * Создает новый JiraResource
     *
     * @param restClient экземпляр REST клиента
     * @param json       объект JSON из которого нужно прочитать поля
     * @throws JiraException в случае ошибки парсинга
     */
    protected JiraResource(RestClient restClient, JSONObject json) throws JiraException {
        this.restClient = restClient;
        if (json != null) {
            deserialize(json);
        }
    }

    /**
     * Получает объект типа "extends JiraResource" по заданному url
     *
     * @param restClient REST client instance
     * @return a list of boards
     * @throws JiraException when the retrieval fails
     */
    protected static <T extends JiraResource> T get(RestClient restClient, Class<T> type, String url) throws JiraException {
        JSON json;
        try {
            json = restClient.get(url);
        } catch (Exception ex) {
            throw new JiraException("Не удается получить " + type.getSimpleName() + " : " + url, ex);
        }

        return getResource(type, json, restClient);
    }

    /**
     * Извлекает объект типа "extends JiraResource" из JSON объекта
     *
     * @param type       тип ресурса, который требуется получить из JSON
     * @param json       экземпляр JSONObject для парсинга
     * @param restClient экземпляр REST client для создания JiraResource
     * @return экземпляр заданного ресурса или null если json не является экзепляром JSONObject
     * @throws JiraException в случае ошибки распознавания
     */
    protected static <T> T getResource(Class<T> type, JSON json, RestClient restClient) throws JiraException {

        if (!(json instanceof JSONObject)) {
            throw new JiraException("Ошибка формата JSON объекта.");
        }

        T result = null;

        if (!((JSONObject) json).isNullObject()) {
            try {
                Constructor<T> constructor = type.getDeclaredConstructor(RestClient.class, JSONObject.class);
                result = constructor.newInstance(restClient, (JSONObject) json);
            } catch (Exception e) {
                throw new JiraException("Ошибка парсинга JSON объекта.", e);
            }
        }

        return result;
    }

    /**
     * Извлекает спискок ресурсов (extends JiraResource) из заданного JSON.
     *
     * @param type       тип ресурса, который требуется получить из JSON
     * @param json  экземпляр JSONObject для парсинга
     * @param restClient экземпляр REST client для создания JiraResource[]
     * @param listName   The name of the list of items from the JSON result.
     * @return список ресурсов типа type из заданного jsonArray
     * @throws JiraException в случае ошибки распознавания
     */
    protected static <T> List<T> getResourceArray(Class<T> type, JSON json, RestClient restClient, String listName) throws JiraException {
        if (!(json instanceof JSONObject)) {
            throw new JiraException("Ошибка формата JSON объекта.");
        }

        JSONObject jo = (JSONObject) json;

        if (!jo.containsKey(listName) || !(jo.get(listName) instanceof JSONArray)) {
            throw new JiraException("Не найдет массив с именем '" + listName + "'");
        }

        List<T> results = new ArrayList<>();

        for (Object v : (JSONArray) jo.get(listName)) {
            T item;
            if (v  instanceof JSON) {
                item = getResource(type, (JSON) v, restClient);
            } else {
                item = (T) v;
            }

            if (item != null) {
                results.add(item);
            }
        }

        return results;
    }

    /**
     * Парсинг json и извлечение стандартных атрибутов. Остальные атрибуты сохраняются в attributes
     * для дальнейшей обработки
     *
     * @param json JSON объект для парсинга
     */
    protected void deserialize(JSONObject json) throws JiraException {
        id = Field.getLong(json.get("id"));
        name = Field.getString(json.get("name"));
        self = Field.getString(json.get("self"));
        addAttributes(json);
    }

    /**
     * Извлекатет указанный артибут как Object.
     *
     * @param name имя атрибута для извлечения.
     * @return значение атрибута.
     */
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    /**
     * Добавляет JSON атрибуты.
     *
     * @param json The json object to extract attributes from.
     */
    void addAttributes(JSONObject json) {
        attributes.putAll(json);
    }

    @Override
    public String toString() {
        return String.format("%s{id=%s, name='%s'}", getClass().getSimpleName(), id, name);
    }
}
