public class DumpTokens {
    public static void main(String[] args) throws Exception {
        syntactic_analysis.Scanner s = new syntactic_analysis.Scanner("Examples/minimal_action.etsl");
        syntactic_analysis.Token t;
        do {
            t = s.Scan();
            System.out.println(t.kind + ": " + t.val);
        } while (t.kind != 0);
    }
}
