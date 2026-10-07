package com.onrender.zipai.repository;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import com.onrender.zipai.domain.RoomOffer;

public interface RoomOfferRepository extends CrudRepository<RoomOffer, Long> {

    List<RoomOffer> findAllByOwnerUserIdOrderByOfferIdDesc(Long ownerUserId);
}
