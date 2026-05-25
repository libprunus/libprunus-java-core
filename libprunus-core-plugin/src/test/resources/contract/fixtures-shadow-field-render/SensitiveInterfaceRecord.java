package contract;

public record SensitiveInterfaceRecord(String a, String b) implements SensitiveRecordInterface {

    public SensitiveInterfaceRecord() {
        this("alpha-val", "beta-val");
    }
}
