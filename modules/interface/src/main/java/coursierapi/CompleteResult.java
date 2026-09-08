package coursierapi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CompleteResult {

    private final int from;
    private final List<String> completions;

    private CompleteResult(int from, List<String> completions) {
        this.from = from;
        this.completions = Collections.unmodifiableList(new ArrayList<>(completions));
    }

    public static CompleteResult of(int from, List<String> completions) {
        return new CompleteResult(from, completions);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj instanceof CompleteResult) {
            CompleteResult other = (CompleteResult) obj;
            return this.from == other.from && this.completions.equals(other.completions);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 37 * (37 * (17 + Integer.hashCode(from))) + completions.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("CompleteResult(from=");
        b.append(from);
        b.append(", completions=[");
        boolean isFirst = true;
        for (String completion : completions) {
            if (isFirst) {
                isFirst = false;
            } else {
                b.append(", ");
            }

            b.append(completion);
        }
        b.append("])");
        return b.toString();
    }

    public int getFrom() {
        return from;
    }

    public List<String> getCompletions() {
        return completions;
    }
}
