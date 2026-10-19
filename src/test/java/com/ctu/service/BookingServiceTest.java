package com.ctu.service;

import com.ctu.exception.PaymentFailedException;
import com.ctu.exception.RoomNotAvailableException;
import com.ctu.model.BookingReceipt;
import com.ctu.model.Room;
import com.ctu.repository.RoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingService Unit Test Suite")
class BookingServiceTest {

    private static final String ROOM_ID = "R101";
    private static final String CARD = "4111-1111-1111-1111";

    @Mock
    private RoomRepository roomRepository;   // gia lap CSDL
    @Mock
    private PaymentGateway paymentGateway;   // gia lap cong thanh toan

    @InjectMocks
    private BookingService bookingService;

    // ---------- 1. Luong thanh cong ----------

    @Test
    @DisplayName("Dat phong thanh cong: phong chuyen isAvailable=false, save() va charge() goi dung 1 lan")
    void testBookRoom_Success() {
        // ARRANGE
        Room room = new Room(ROOM_ID, 500_000, true);
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
        when(paymentGateway.charge(CARD, 1_500_000)).thenReturn(true);

        // ACT
        BookingReceipt receipt = bookingService.bookRoom(ROOM_ID, 3, CARD);

        // ASSERT
        assertNotNull(receipt);
        assertNotNull(receipt.getBookingId());
        assertFalse(receipt.getBookingId().isEmpty());
        assertEquals(ROOM_ID, receipt.getRoomId());
        assertEquals(1_500_000, receipt.getTotalAmount(), 0.001);
        assertFalse(room.isAvailable(), "Phong phai chuyen sang trang thai da dat");

        verify(roomRepository, times(1)).findById(ROOM_ID);
        verify(paymentGateway, times(1)).charge(CARD, 1_500_000);
        verify(roomRepository, times(1)).save(room);
    }

    // ---------- 2. Ngoai le nghiep vu ----------

    @Test
    @DisplayName("Nem RoomNotAvailableException khi findById() tra ve Optional.empty()")
    void testBookRoom_RoomNotFound_ThrowsException() {
        when(roomRepository.findById("R999")).thenReturn(Optional.empty());

        RoomNotAvailableException ex = assertThrows(RoomNotAvailableException.class,
                () -> bookingService.bookRoom("R999", 2, CARD));

        assertEquals("Khong tim thay phong: R999", ex.getMessage());
        verify(paymentGateway, never()).charge(anyString(), anyDouble());
        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("Nem RoomNotAvailableException khi phong da duoc dat (isAvailable == false)")
    void testBookRoom_RoomAlreadyBooked_ThrowsException() {
        Room bookedRoom = new Room(ROOM_ID, 500_000, false);
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(bookedRoom));

        RoomNotAvailableException ex = assertThrows(RoomNotAvailableException.class,
                () -> bookingService.bookRoom(ROOM_ID, 2, CARD));

        assertEquals("Phong da duoc dat truoc do: " + ROOM_ID, ex.getMessage());
        verify(paymentGateway, never()).charge(anyString(), anyDouble());
        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("Nem PaymentFailedException khi charge() tra ve false; phong khong bi khoa")
    void testBookRoom_PaymentFailed_ThrowsException() {
        Room room = new Room(ROOM_ID, 500_000, true);
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
        when(paymentGateway.charge(CARD, 1_000_000)).thenReturn(false);

        PaymentFailedException ex = assertThrows(PaymentFailedException.class,
                () -> bookingService.bookRoom(ROOM_ID, 2, CARD));

        assertEquals("Thanh toan qua the credit card that bai!", ex.getMessage());
        assertTrue(room.isAvailable(), "Thanh toan loi thi phong van con trong");
        verify(paymentGateway, times(1)).charge(CARD, 1_000_000);
        verify(roomRepository, never()).save(any());
    }

    // ---------- 3. Tham so bien & Edge cases ----------

    @ParameterizedTest(name = "nights = {0} -> IllegalArgumentException")
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    @DisplayName("Nem IllegalArgumentException khi nights <= 0")
    void testBookRoom_InvalidNights_ThrowsException(int nights) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> bookingService.bookRoom(ROOM_ID, nights, CARD));

        assertEquals("Tham so dat phong khong hop le!", ex.getMessage());
        verifyNoInteractions(roomRepository, paymentGateway);
    }

    @ParameterizedTest(name = "creditCard = \"{0}\" -> IllegalArgumentException")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Nem IllegalArgumentException khi creditCard null / rong / chi co khoang trang")
    void testBookRoom_NullOrEmptyCreditCard_ThrowsException(String creditCard) {
        assertThrows(IllegalArgumentException.class,
                () -> bookingService.bookRoom(ROOM_ID, 2, creditCard));

        verifyNoInteractions(roomRepository, paymentGateway);
    }

    @Test
    @DisplayName("Nem IllegalArgumentException khi roomId == null")
    void testBookRoom_NullRoomId_ThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> bookingService.bookRoom(null, 2, CARD));

        verifyNoInteractions(roomRepository, paymentGateway);
    }

    @Test
    @DisplayName("Bien duoi: nights = 1 la hop le")
    void testBookRoom_MinimumNights_Success() {
        Room room = new Room(ROOM_ID, 300_000, true);
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
        when(paymentGateway.charge(CARD, 300_000)).thenReturn(true);

        BookingReceipt receipt = bookingService.bookRoom(ROOM_ID, 1, CARD);

        assertEquals(300_000, receipt.getTotalAmount(), 0.001);
        verify(roomRepository, times(1)).save(room);
    }
}
