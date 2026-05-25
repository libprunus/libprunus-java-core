package contract;

public class TransientDollarPrefixDto {

    public int normalField = 7;

    public transient int tx = 11;

    public int $dollarPrefix = 13;
}
