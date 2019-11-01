package template;

import java.util.ArrayList;
import java.util.List;

public class Next {
    // Attributes
    public Obj current;
    public Obj next;

    // Methods
    public Next(Obj current, Obj next) {

        this.current = current;
        this.next = next;
    }

    public Obj getNext() {

        return this.next;
    }

    public void setNext(Obj next) {

        this.next = next;
    }
}
