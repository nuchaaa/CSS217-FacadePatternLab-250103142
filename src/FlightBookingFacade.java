public class FlightBookingFacade {
    private final SeatInventorySystem seatSystem;
    private final BaggagePricingEngine baggageEngine;
    private final CurrencyConverter currencyConverter;
    private final PaymentProcessor paymentProcessor;
    private final TicketIssuer ticketIssuer;
    public FlightBookingFacade() {
        seatSystem = new SeatInventorySystem();
        baggageEngine = new BaggagePricingEngine();
        currencyConverter = new CurrencyConverter();
        paymentProcessor = new PaymentProcessor();
        ticketIssuer = new TicketIssuer();
    }
    public String bookFlight(String passengerName, String flightCode, String seatNumber,
    double baseFareUSD, int bagCount, double bagWeightKg,
    String currency, String cardNumber) {
        return bookFlight(passengerName, flightCode, seatNumber, baseFareUSD,
        bagCount, bagWeightKg, currency, cardNumber, "STANDARD");
    }
    public String bookFlight(String passengerName, String flightCode, String seatNumber,
    double baseFareUSD, int bagCount, double bagWeightKg,
    String currency, String cardNumber, String loyaltyTier) {
        String lockToken = null;
        try {
            seatSystem.connectToReservationServer();
            lockToken = seatSystem.lockSeat(flightCode, seatNumber, passengerName);
            boolean silver = "SILVER".equalsIgnoreCase(loyaltyTier);
            boolean gold = "GOLD".equalsIgnoreCase(loyaltyTier);
            double baggageFeeUSD = silver || gold ? 0.0
            : baggageEngine.calculateBaggageFeeUSD(bagCount, bagWeightKg);
            double totalUSD = baseFareUSD + baggageFeeUSD;
            double convertedTotal = currencyConverter.convertFromUSD(totalUSD, currency);
            paymentProcessor.initializeGateway();
            paymentProcessor.applyPromoCode(silver ? "SILVER-FREE-BAG" : null);
            String receiptId = paymentProcessor.chargeCard(cardNumber, convertedTotal, currency);
            return ticketIssuer.generateTicket(passengerName, flightCode, lockToken, receiptId);
        } catch (RuntimeException ex) {
            if (lockToken != null) {
                seatSystem.releaseSeatLock(lockToken);
            }
            String failure = "BOOKING_FAILED: " + ex.getMessage();
            System.out.println(failure);
            return failure;
        } finally {
            seatSystem.disconnect();
        }
    }
}
