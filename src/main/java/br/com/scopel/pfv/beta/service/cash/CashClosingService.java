package br.com.scopel.pfv.beta.service.cash;

import br.com.scopel.pfv.beta.exception.CashRegisterClosedException;
import br.com.scopel.pfv.beta.exception.RegisterNotFindException;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.repository.CashRegisterRepository;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CashClosingService {

    public static final int MAX_EXTRA_NOTE_LENGTH = 155;

    private final CashRegisterRepository registerRepository;
    private final CashRegisterService cashRegisterService;
    private final CashSummaryService cashSummaryService;

    public CashClosingService(CashRegisterRepository registerRepository, CashRegisterService cashRegisterService,
                              CashSummaryService cashSummaryService) {
        this.registerRepository = registerRepository;
        this.cashRegisterService = cashRegisterService;
        this.cashSummaryService = cashSummaryService;
    }

    @Transactional
    public CashRegister close(Long cashRegisterId, ClosingCount counted) {
        validate(counted);
        String extraNote = normalizeExtraNote(counted.extraNote());
        CashRegister register = registerRepository.findById(cashRegisterId)
                .orElseThrow(() -> new RegisterNotFindException("Caixa não registrado!"));
        if (register.getStatus() != CashRegister.Status.OPEN) {
            throw new CashRegisterClosedException();
        }
        CashSummary expected = cashSummaryService.overview(register).summary();
        if (counted.extra().compareTo(expected.extra()) != 0 && extraNote == null) {
            throw new IllegalArgumentException(
                    "Informe a observação do Extra: o valor contado é diferente do esperado.");
        }
        CashRegister closed = cashRegisterService.close(cashRegisterId, counted.cash());
        closed.setExpectedPix(expected.pix());
        closed.setCountedPix(counted.pix());
        closed.setExpectedCredit(expected.credit());
        closed.setCountedCredit(counted.credit());
        closed.setExpectedDebit(expected.debit());
        closed.setCountedDebit(counted.debit());
        closed.setExpectedExtra(expected.extra());
        closed.setCountedExtra(counted.extra());
        closed.setExtraNote(extraNote);
        return registerRepository.save(closed);
    }

    private void validate(ClosingCount counted) {
        if (counted == null || counted.cash() == null || counted.pix() == null
                || counted.credit() == null || counted.debit() == null || counted.extra() == null) {
            throw new IllegalArgumentException("Informe o valor contado de cada forma de pagamento.");
        }
        if (counted.cash().signum() < 0 || counted.pix().signum() < 0
                || counted.credit().signum() < 0 || counted.debit().signum() < 0
                || counted.extra().signum() < 0) {
            throw new IllegalArgumentException("O valor contado não pode ser negativo.");
        }
    }

    private String normalizeExtraNote(String extraNote) {
        String normalized = extraNote == null ? "" : extraNote.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > MAX_EXTRA_NOTE_LENGTH) {
            throw new IllegalArgumentException(
                    "A observação do Extra aceita no máximo " + MAX_EXTRA_NOTE_LENGTH + " caracteres.");
        }
        return normalized;
    }
}
