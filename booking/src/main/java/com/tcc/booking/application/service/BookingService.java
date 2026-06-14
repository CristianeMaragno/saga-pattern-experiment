package com.tcc.booking.application.service;

import org.springframework.stereotype.Service;

import com.tcc.booking.application.dto.BookingResponseDTO;
import com.tcc.booking.application.dto.CreateSolicitacaoRequestDTO;
import com.tcc.booking.application.dto.SolicitacaoResponseDTO;
import com.tcc.booking.domain.entity.Solicitacao;
import com.tcc.booking.domain.repository.SolicitacaoRepository;
import com.tcc.booking.hold.dto.CreateHoldRequest;
import com.tcc.booking.hold.dto.HoldResponse;
import com.tcc.booking.hold.service.HoldService;

@Service
public class BookingService {

    private final SolicitacaoRepository solicitacaoRepository;
    private final HoldService holdService;

    public BookingService(SolicitacaoRepository solicitacaoRepository, HoldService holdService) {
        this.solicitacaoRepository = solicitacaoRepository;
        this.holdService = holdService;
    }

    /**
     * Cria uma solicitação e cria holds de voo e hotel.
     * - Flight hold: duração respeita [24h,72h] (serviço de hold aplica validação)
     * - Hotel hold: duração default aplicada pelo serviço de hold (48h)
     */
    public BookingResponseDTO createBooking(CreateSolicitacaoRequestDTO req) {
        // Persistir solicitação
        Solicitacao s = new Solicitacao(null, req.getUsuarioId(), req.getDestino(), req.getDataIda(), req.getDataVolta(), req.getMotivo());
        Solicitacao saved = solicitacaoRepository.save(s);

        // Usar id da solicitação como referência para os holds
        String reference = "solicitacao:" + saved.getId();

        // Criar flight hold (sem duração explícita -> o HoldService usará FLIGHT_MIN)
        CreateHoldRequest flightReq = new CreateHoldRequest(reference, null);
        HoldResponse flightHold = holdService.createFlightHold(flightReq);

        // Criar hotel hold (sem duração explícita -> o HoldService usará HOTEL_DEFAULT)
        CreateHoldRequest hotelReq = new CreateHoldRequest(reference, null);
        HoldResponse hotelHold = holdService.createHotelHold(hotelReq);

        SolicitacaoResponseDTO solDto = SolicitacaoResponseDTO.from(saved);
        return new BookingResponseDTO(solDto, flightHold, hotelHold);
    }
}
