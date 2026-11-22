package com.fashionapp.domain.purchase;

import com.fashionapp.domain.user.UserPrincipal;
import com.fashionapp.global.common.ApiResponse;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import com.fashionapp.global.jwt.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/purchase-checks")
@RequiredArgsConstructor
public class PurchaseCheckController {

    private final PurchaseCheckService purchaseCheckService;
    private final PurchaseRecordService purchaseRecordService;

    /** FR-11: compare a photographed piece with the closet. price (won) is optional. */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PurchaseCheckResponse>> check(
            @CurrentUser UserPrincipal userPrincipal,
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "price", required = false) String price) {
        return ResponseEntity.ok(ApiResponse.success(
                purchaseCheckService.check(userPrincipal.getId(), image, price)));
    }

    /** FR-12: the latest 20 checks, newest first. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<PurchaseRecordResponse>>> history(
            @CurrentUser UserPrincipal userPrincipal) {
        return ResponseEntity.ok(ApiResponse.success(purchaseRecordService.history(userPrincipal.getId())));
    }

    /** FR-12: one check of the current user (someone else's is 404). */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PurchaseRecordResponse>> get(
            @CurrentUser UserPrincipal userPrincipal, @PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.success(purchaseRecordService.get(userPrincipal.getId(), checkId(id))));
    }

    /** FR-12: BUY moves the photo into the closet as a new piece, SKIP deletes it. Expired = 410. */
    @PostMapping("/{id}/decision")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PurchaseDecisionResponse>> decide(
            @CurrentUser UserPrincipal userPrincipal, @PathVariable("id") String id,
            @RequestBody(required = false) PurchaseDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                purchaseRecordService.decide(userPrincipal.getId(), checkId(id), choiceOf(request))));
    }

    /** FR-13: re-judge with corrected attributes (omitted = unchanged), skipping classification. Decided = 409. */
    @PatchMapping("/{id}/attributes")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PurchaseCheckResponse>> recheck(
            @CurrentUser UserPrincipal userPrincipal, @PathVariable("id") String id,
            @RequestBody(required = false) PurchaseAttributesRequest request) {
        PurchaseAttributesRequest body = request == null
                ? new PurchaseAttributesRequest(null, null, null, null, null, null, null) : request;
        return ResponseEntity.ok(ApiResponse.success(
                purchaseCheckService.recheck(userPrincipal.getId(), checkId(id), body)));
    }

    /** A malformed id cannot be anyone's check: 404 like a missing one. */
    private static UUID checkId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.PURCHASE_CHECK_NOT_FOUND);
        }
    }

    private static PurchaseDecisionRequest.Choice choiceOf(PurchaseDecisionRequest request) {
        if (request == null) {
            throw new CustomException(ErrorCode.INVALID_PURCHASE_DECISION);
        }
        return request.choice();
    }
}
