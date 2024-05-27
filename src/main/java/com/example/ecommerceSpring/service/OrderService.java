package com.example.ecommerceSpring.service;

import com.example.ecommerceSpring.dtos.Cart.CartItemDto;
import com.example.ecommerceSpring.dtos.Cart.OrderDto;
import com.example.ecommerceSpring.dtos.users.UserDto;
import com.example.ecommerceSpring.entities.CartProductEntity;
import com.example.ecommerceSpring.entities.OrderEntity;
import com.example.ecommerceSpring.entities.OrderProductEntity;
import com.example.ecommerceSpring.entities.UserEntity;
import com.example.ecommerceSpring.enums.OrderStatusEnum;
import com.example.ecommerceSpring.exception.CustomException;
import com.example.ecommerceSpring.repositories.OrderRepository;
import io.micrometer.common.util.StringUtils;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;

import static com.example.ecommerceSpring.enums.OrderStatusEnum.CONFIRMED;

public class OrderService<T> {
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ModelMapper modelMapper;
    @Autowired
    private UserService userService;
    @Autowired
    private CartService cartService;

    @Autowired
    private OrderProductService orderProductService;

    private UserDto currentUser;


    public OrderService() {
        //we need to first validate if the logged user have a or
    }

    public boolean createOrder(OrderDto order) {
        currentUser = userService.authenticatedUser();
        //validations
        if (currentUser == null) {
            return false;
        }
        if (orderRepository.findById(order.getId()).isPresent()) {
            throw new CustomException("Order already exist", HttpStatus.CONFLICT);
        }
        // first we create the order with shipping and card info
        if (StringUtils.isBlank(order.getOrderStatus().toString()) ||
                StringUtils.isBlank(order.getCity()) ||
                StringUtils.isBlank(order.getState()) ||
                StringUtils.isBlank(order.getZipCode()) ||
                StringUtils.isBlank(order.getCardNumber()) ||
                StringUtils.isBlank(order.getAddres1()) ||
                StringUtils.isBlank(order.getAddres2())) {
            throw new CustomException("No empty fields acepted", HttpStatus.BAD_REQUEST);
        }

        if (order.getOrderStatus() == null) {
            throw new CustomException("Invalid order status", HttpStatus.BAD_REQUEST);
        }
        order.setUser(modelMapper.map(currentUser, UserEntity.class));
//        order.setOrderStatus(statusConverter(order.getOrderStatus()));
        OrderEntity orderInfoEntity = modelMapper.map(order, OrderEntity.class);
        orderInfoEntity.setOrderStatus(order.getOrderStatus());
        OrderEntity newOrder = orderRepository.save(orderInfoEntity);
        //sencond we transfer the items from cart to the middle table of order
        List<OrderProductEntity> cartItemsToOrderItem = getOrderProductEntities(newOrder);

        if (!orderProductService.save(cartItemsToOrderItem).isEmpty()) {
            cartService.clearCart();
            return true;
        }
        return false;
    }

    private List<OrderProductEntity> getOrderProductEntities(OrderEntity newOrder) {
        List<CartProductEntity> cartItems = cartService.getCartProductItems();
        //we have to valdiate if the user have item in cart
        if (cartItems == null || cartItems.isEmpty()) {
            throw new CustomException("After place an order you have to add products in your cart", HttpStatus.BAD_REQUEST);
        }

        List<OrderProductEntity> cartItemsToOrderItem = new ArrayList<>();
        for (CartProductEntity cartItem : cartItems) {
            OrderProductEntity newOrderItem = new OrderProductEntity(0, cartItem.getQuantity(), newOrder, cartItem.getProduct());
            cartItemsToOrderItem.add(newOrderItem);
        }
        return cartItemsToOrderItem;
    }

    private OrderStatusEnum statusConverter(String status) {

        switch (status.toLowerCase()) {
            case "confirmed":
                return OrderStatusEnum.CONFIRMED;
            case "canceled":
                return OrderStatusEnum.CANCELED;
            case "pending":
                return OrderStatusEnum.PENDING;
            default:
                throw new IllegalArgumentException("Unknown order status: " + status);
        }
    }

    public List<OrderEntity> getCurrentUserOrdersInfo() {
        currentUser = userService.authenticatedUser();
        List<OrderEntity> orders = orderRepository.findByUser_Id(currentUser.getId());
        return orders;
    }
}