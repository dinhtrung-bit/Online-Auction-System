package server.networks.dto;

public class MessageDTO {
    private String action;      // hành động làm để thực hiện chuyển dữ liệu thành json
    private String payload;     // dữ liệu cần chuyển

    public MessageDTO() {
    }
    public MessageDTO(String action, String payload) {
        this.action = action;
        this.payload = payload;
    }
    public String getAction() {
        return action;
    }
    public void setAction(String action) {
        this.action = action;
    }
    public String getPayload() {
        return payload;
    }
    public void setPayload(String payload) {
        this.payload = payload;
    }
}