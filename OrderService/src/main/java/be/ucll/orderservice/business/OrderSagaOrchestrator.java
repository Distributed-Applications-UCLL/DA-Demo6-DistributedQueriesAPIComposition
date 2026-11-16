package be.ucll.orderservice.business;

import be.ucll.orderservice.messaging.OrderEventPublisher;
import be.ucll.orderservice.messaging.dto.*;
import be.ucll.orderservice.persistence.Order;
import be.ucll.orderservice.persistence.OrderRepository;
import be.ucll.orderservice.persistence.OrderStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OrderSagaOrchestrator {

        private final OrderRepository orderRepository;
        private final OrderEventPublisher publisher;

        public OrderSagaOrchestrator(
                OrderRepository orderRepository,
                OrderEventPublisher publisher) {

            this.orderRepository = orderRepository;
            this.publisher = publisher;
        }

        public void startSaga(Order order) {

            // The first event ID is also the Saga ID.
            String eventId = UUID.randomUUID().toString();

            OrderCreatedEvent event = new OrderCreatedEvent(
                    eventId,
                    eventId,
                    order.getId(),
                    order.getCustomer(),
                    order.getTotal()
            );

            publisher.publishOrderCreated(event);
        }

        public void handlePaymentSucceeded(PaymentSucceededEvent event) {

            CreateDeliveryCommand command = new CreateDeliveryCommand(
                    UUID.randomUUID().toString(),
                    event.sagaId(),
                    event.orderId()
            );

            publisher.publishCreateDelivery(command);
        }

        @Transactional
        public void handlePaymentFailed(PaymentFailedEvent event) {

            Order order = findOrder(event.orderId());

            order.setStatus(OrderStatus.CANCELLED);

            orderRepository.save(order);
        }

        @Transactional
        public void handleDeliveryCreated(DeliveryCreatedEvent event) {

            Order order = findOrder(event.orderId());

            order.setStatus(OrderStatus.CONFIRMED);

            orderRepository.save(order);
        }

        public void handleDeliveryFailed(DeliveryFailedEvent event) {

            RefundPaymentCommand command = new RefundPaymentCommand(
                    UUID.randomUUID().toString(),
                    event.sagaId(),
                    event.orderId()
            );

            publisher.publishRefundPayment(command);
        }

        @Transactional
        public void handlePaymentRefunded(PaymentRefundedEvent event) {

            Order order = findOrder(event.orderId());

            order.setStatus(OrderStatus.CANCELLED);

            orderRepository.save(order);
        }

        private Order findOrder(Long orderId) {

            return orderRepository.findById(orderId)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Order not found: " + orderId
                            )
                    );
        }
}
