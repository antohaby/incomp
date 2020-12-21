package root;

public class C4 {
    public C2 c2;
    public C3 c3;

    public C4() {
        this.c2 = new C2();
        this.c3 = new C3();
    }

    public static C2 staticC2 = new C2();
    public static C2 staticFunC2() {
        return new C2();
    }

    public int a() {
        return 42;
    }
}