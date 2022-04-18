package spbpu.md.converter;

import java.util.LinkedList;
import java.util.StringJoiner;

/**
 * Reporter for {@link ConvertException}.
 */
public class ErrorReporter {

    private final LinkedList<Exception> _errorList = new LinkedList<>();

    /**
     * Add error to inner list and print stack trace.
     * @param err {@link Exception} to add
     */
    public void addError(Exception err) {
        err.printStackTrace();
        _errorList.add(err);
    }

    /**
     * Get reports with all exception occurred during conversion.
     * @return united {@link Exception#getMessage() messages} of reported errors
     */
    public String getReport() {
        StringJoiner result = new StringJoiner(
                "\n==========================================================================================\n"
        );

        for (Exception err : _errorList) {
            result.add(err.getMessage());
        }

        return result.toString();
    }
}
