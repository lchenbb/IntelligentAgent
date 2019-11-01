package template;

public class Obj {

    public enum Type {
        PickUp,
        Vehicle,
        Delivery
    }

    public Type type;
    public Obj next;
    public int time;
}
