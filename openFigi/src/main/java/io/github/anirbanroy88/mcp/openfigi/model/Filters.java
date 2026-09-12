package io.github.anirbanroy88.mcp.openfigi.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.anirbanroy88.mcp.openfigi.client.OpenFigiException;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Filters(String exchCode, String micCode, String currency, String marketSecDes,
        String securityType, String securityType2, Boolean includeUnlistedEquities,
        String optionType, List<BigDecimal> strike, List<BigDecimal> contractSize,
        List<BigDecimal> coupon, List<String> expiration, List<String> maturity, String stateCode) {

    public void validate() {
        if (exchCode != null && micCode != null) throw OpenFigiException.invalid("Use exchCode or micCode, not both");
        for (String value : new String[]{exchCode, micCode, currency, marketSecDes, securityType,
                securityType2, optionType, stateCode}) {
            if (value != null && (value.isBlank() || value.length() > 256))
                throw OpenFigiException.invalid("Filter strings must be nonblank and at most 256 characters");
        }
        if (optionType != null && !List.of("Call", "Put").contains(optionType))
            throw OpenFigiException.invalid("optionType must be Call or Put");
        numericRange(strike); numericRange(contractSize); numericRange(coupon);
        dateRange(expiration); dateRange(maturity);
        if (("Option".equals(securityType2) || "Warrant".equals(securityType2)) && expiration == null)
            throw OpenFigiException.invalid("expiration is required for Option or Warrant");
        if ("Pool".equals(securityType2) && maturity == null)
            throw OpenFigiException.invalid("maturity is required for Pool");
    }
    private static void numericRange(List<BigDecimal> range) {
        if (range == null) return;
        if (range.size() != 2 || (range.get(0) == null && range.get(1) == null))
            throw OpenFigiException.invalid("Numeric intervals require two bounds, at least one non-null");
        if (range.get(0) != null && range.get(1) != null && range.get(0).compareTo(range.get(1)) > 0)
            throw OpenFigiException.invalid("Interval lower bound must not exceed upper bound");
    }
    private static void dateRange(List<String> range) {
        if (range == null) return;
        if (range.size() != 2 || (range.get(0) == null && range.get(1) == null))
            throw OpenFigiException.invalid("Date intervals require two bounds, at least one non-null");
        try {
            LocalDate from = parseDate(range.get(0));
            LocalDate to = parseDate(range.get(1));
            if (from != null && to != null && (from.isAfter(to) || to.isAfter(from.plusYears(1))))
                throw OpenFigiException.invalid("Date interval must be ordered and span at most one year");
        } catch (DateTimeParseException e) {
            throw OpenFigiException.invalid("Dates must use YYYY-MM-DD");
        }
    }
    private static LocalDate parseDate(String value) {
        if (value == null) return null;
        if (!value.matches("\\d{4}-\\d{2}-\\d{2}")) throw OpenFigiException.invalid("Dates must use YYYY-MM-DD");
        return LocalDate.parse(value);
    }
}
