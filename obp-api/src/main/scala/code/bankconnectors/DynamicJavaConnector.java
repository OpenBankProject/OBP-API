package code.bankconnectors;

import com.openbankproject.commons.model.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * This is a java dynamic function template.
 * Must copy the whole content of the file as "dynamic method body".
 * Currently, support Java 1.8 version language features
 */
public class DynamicJavaConnector implements Supplier<Function<Object[], Object>> {
    private Object apply(Object[] args) {
       BankId bankId = (BankId) args[0];

       Bank bank = new BankCommons(bankId, "The Royal Bank of Scotland",
               "The Royal Bank of Scotland",
               "https://www.example.tesobe.com/logo.gif",
               "https://www.example.tesobe.com",
               "OBP",
               "rbs",
               "Swift bic value",
               "national-identifier-value"
               );
       return bank;
    }

    @Override
    public Function<Object[], Object> get() {
        return this::apply;
    }
}
