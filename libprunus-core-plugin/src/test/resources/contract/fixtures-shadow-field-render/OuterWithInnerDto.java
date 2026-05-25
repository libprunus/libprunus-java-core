package contract;

public class OuterWithInnerDto {

    public String outerField = "outer-val";

    public Inner inner = new Inner();

    public class Inner {

        public String innerField = "inner-val";
    }
}
