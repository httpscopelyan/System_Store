package br.com.scopel.pfv.beta.service.cash;

import br.com.scopel.pfv.beta.model.CashMovement;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.CashRegisterRepository;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import br.com.scopel.pfv.beta.service.ReceiptService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
public class CashReopeningService {

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final UserRepository userRepository;
    private final CashRegisterRepository registerRepository;
    private final CashRegisterService cashRegisterService;
    private final CashSummaryService cashSummaryService;
    private final ReceiptService receiptService;
    private final JdbcTemplate jdbc;

    public CashReopeningService(UserRepository userRepository, CashRegisterRepository registerRepository,
                                CashRegisterService cashRegisterService, CashSummaryService cashSummaryService,
                                ReceiptService receiptService, JdbcTemplate jdbc) {
        this.userRepository = userRepository;
        this.registerRepository = registerRepository;
        this.cashRegisterService = cashRegisterService;
        this.cashSummaryService = cashSummaryService;
        this.receiptService = receiptService;
        this.jdbc = jdbc;
    }

    public boolean operatorExists(String login) {
        return userRepository.findByLogin(login).isPresent();
    }

    @Transactional
    public Optional<CashRegister> reopenLastClosed(String login, String reason, Path backupDirectory) {
        Optional<User> operator = userRepository.findByLogin(login);
        if (operator.isEmpty()
                || registerRepository.findByOperatorAndStatus(operator.get(), CashRegister.Status.OPEN).isPresent()) {
            return Optional.empty();
        }
        Optional<CashRegister> lastClosed = registerRepository
                .findFirstByOperatorAndStatusOrderByClosedAtDesc(operator.get(), CashRegister.Status.CLOSED);
        if (lastClosed.isEmpty()) {
            return Optional.empty();
        }
        CashRegister register = lastClosed.get();
        List<CashMovement> movements = cashRegisterService.movements(register);
        String closingReceipt = receiptService.buildCashCloseReceipt(register, movements);
        String movementsReport = receiptService.buildCashMovementsReceipt(register,
                cashSummaryService.overview(register));
        writeBackup(backupDirectory, register, closingReceipt + "\n\n" + movementsReport);
        jdbc.update("INSERT INTO cash_register_reopening (cash_register_id, reopened_at, reason,"
                        + " previous_closed_at, previous_expected_amount, previous_closing_amount,"
                        + " previous_difference, closing_receipt) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                register.getId(), LocalDateTime.now(), reason, register.getClosedAt(),
                register.getExpectedAmount(), register.getClosingAmount(), register.getDifference(), closingReceipt);
        clearClosing(register);
        return Optional.of(registerRepository.save(register));
    }

    private void clearClosing(CashRegister register) {
        register.setStatus(CashRegister.Status.OPEN);
        register.setClosedAt(null);
        register.setExpectedAmount(null);
        register.setClosingAmount(null);
        register.setDifference(null);
        register.setExpectedPix(null);
        register.setCountedPix(null);
        register.setExpectedCredit(null);
        register.setCountedCredit(null);
        register.setExpectedDebit(null);
        register.setCountedDebit(null);
        register.setExpectedExtra(null);
        register.setCountedExtra(null);
        register.setExtraNote(null);
    }

    private void writeBackup(Path directory, CashRegister register, String content) {
        try {
            Files.createDirectories(directory);
            Path file = directory.resolve("caixa-" + register.getId() + "-fechamento-com-erro-"
                    + FILE_STAMP.format(LocalDateTime.now()) + ".txt");
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("Não foi possível gravar o backup do caixa " + register.getId(), failure);
        }
    }
}
