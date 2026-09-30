document.addEventListener('DOMContentLoaded', function() {
    function escapeHtml(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    // 1. 맞춤 대출 찾기 로직
    const filterForm = document.getElementById('loanFilterForm');
    if (filterForm) {
        let loanProducts = [
            // 구입 자금
            {
                name: '내집마련 디딤돌 대출',
                purpose: 'purchase',
                targets: ['general', 'lowincome'],
                limit: '최대 2.5억원 (생애최초 3억)',
                rate: '연 2.45% ~ 3.55%',
                desc: '부부합산 연소득 6천만원 이하(생애최초는 7천만원 이하), 순자산가액 4.69억원 이하 무주택 세대주 대상 주택 구입자금 대출'
            },
            {
                name: '신혼부부 전용 구입자금',
                purpose: 'purchase',
                targets: ['newlywed'],
                limit: '최대 4억원',
                rate: '연 2.15% ~ 3.25%',
                desc: '생애최초로 주택을 구입하는 신혼부부(부부합산 연소득 8.5천만원 이하), 순자산가액 4.69억원 이하 대상 주택 구입자금 대출'
            },
            {
                name: '청년 주택드림 대출',
                purpose: 'purchase',
                targets: ['youth'],
                limit: '최대 6억원 (분양가 80% 이내)',
                rate: '최저 연 2.2%~ (조건별 추가 우대금리 가능)',
                desc: '청년 주택드림 청약통장 가입 기간 1년 이상, 납입 금액 1천만원 이상이며 만 39세 이하 청년 대상 저리 분양자금 대출'
            },
            // 전세 자금
            {
                name: '버팀목 전세자금대출',
                purpose: 'jeonse',
                targets: ['general', 'lowincome'],
                limit: '수도권 1.2억원 / 비수도권 8천만원 이내',
                rate: '연 2.10% ~ 2.90%',
                desc: '근로자 및 서민의 주거안정을 위한 전세자금 대출 (소득 5천만원 이하, 자산 3.45억원 이하 무주택 세대주)'
            },
            {
                name: '신혼부부 전용 전세자금',
                purpose: 'jeonse',
                targets: ['newlywed'],
                limit: '수도권 최대 3억원 / 비수도권 2억원 이내',
                rate: '연 1.20% ~ 2.10%',
                desc: '혼인 7년 이내 신혼부부 또는 3개월 이내 결혼 예정자를 위한 초저금리 전세자금 대출 (부부합산 연소득 7.5천만원 이하)'
            },
            {
                name: '청년 전용 버팀목 전세자금대출',
                purpose: 'jeonse',
                targets: ['youth'],
                limit: '최대 2억원 (임차보증금의 80% 이내)',
                rate: '연 1.50% ~ 2.10%',
                desc: '만 19세 이상 ~ 만 34세 이하 청년 대상 전용 저리 전세자금 대출 (연소득 5천만원 이하, 순자산가액 3.45억원 이하)'
            },
            // 월세 지원
            {
                name: '주거안정 월세대출',
                purpose: 'monthly',
                targets: ['general', 'lowincome'],
                limit: '매월 최대 40만원 (최대 960만원 한도)',
                rate: '우대형 연 1.3% / 일반형 연 1.8%',
                desc: '주거취약계층(우대형) 또는 자립 준비중인 청년 및 일반 무주택자 대상 저리 월세대출 지원'
            },
            {
                name: '청년 전용 보증부월세 대출',
                purpose: 'monthly',
                targets: ['youth'],
                limit: '보증금 최대 5천만원 + 월세 최대 월 50만원 (보증금의 80% 이내)',
                rate: '보증금 연 1.3% / 월세 연 1.0%',
                desc: '만 19세 이상 ~ 34세 이하 청년을 위한 보증금 대출과 월세 대출을 결합한 특화 상품 (소득 5천만원 이하)'
            }
        ];

        filterForm.addEventListener('submit', async function(e) {
            e.preventDefault();
            const target = document.getElementById('targetType').value;
            const purpose = document.getElementById('loanPurpose').value;
            const resultBox = document.getElementById('filterResults');

            try {
                const response = await fetch('/api/finance/policies?category=' + encodeURIComponent(purpose), {
                    credentials: 'same-origin'
                });
                if (!response.ok) throw new Error('금융정책을 불러오지 못했습니다.');
                const policies = await response.json();
                loanProducts = policies.map(function(policy) {
                    return {
                        name: policy.name,
                        purpose: policy.category,
                        targets: [policy.targetType],
                        limit: policy.limitInfo,
                        rate: policy.rateInfo,
                        desc: policy.description,
                        checkedAt: policy.sourceCheckedAt,
                        updateStatus: policy.updateStatus
                    };
                });
            } catch (error) {
                resultBox.innerHTML = '<div style="text-align:center; color:#b42318; padding:40px;"><strong>' + error.message + '</strong><br><span style="font-size:0.9rem;">잠시 후 다시 시도해 주세요.</span></div>';
                return;
            }
            
            // Filter logic
            const matched = loanProducts.filter(p => {
                const matchesPurpose = p.purpose === purpose;
                // matching target or general product (since youth/newlywed/vulnerable qualify for general ones)
                const matchesTarget = p.targets.includes(target) || (target !== 'general' && p.targets.includes('general'));
                return matchesPurpose && matchesTarget;
            });

            if (matched.length > 0) {
                // sort so that target-specific matches appear first
                matched.sort((a, b) => {
                    const aSpecific = a.targets.includes(target);
                    const bSpecific = b.targets.includes(target);
                    if (aSpecific && !bSpecific) return -1;
                    if (!aSpecific && bSpecific) return 1;
                    return 0;
                });

                resultBox.innerHTML = matched.map(product => `
                    <div class="result-item" style="border-left: 4px solid var(--primary-color); margin-bottom: 20px; padding: 20px; background: #fff; border-radius: 8px; box-shadow: 0 4px 6px rgba(0,0,0,0.05);">
                        <div class="d-flex justify-content-between align-items-start mb-2">
                            <h4 style="color: var(--accent-color); font-weight: 700; margin: 0; font-size:1.1rem;">${escapeHtml(product.name)}</h4>
                            <span class="badge ${product.targets.includes(target) ? 'bg-primary' : 'bg-secondary'}" style="font-size: 0.75rem; padding: 4px 8px;">
                                ${product.targets.includes(target) ? '맞춤 상품' : '지원 가능'}
                            </span>
                        </div>
                        <p style="color: #666; font-size: 0.9rem; margin-bottom: 12px; line-height: 1.5;">${escapeHtml(product.desc)}</p>
                        <div class="d-flex flex-wrap gap-3" style="font-size: 0.85rem; color: #555; background: var(--bg-light); padding: 10px 15px; border-radius: 6px; margin-bottom: 12px;">
                            <div style="flex: 1; min-width: 200px;"><strong>한도:</strong> ${escapeHtml(product.limit)}</div>
                            <div style="flex: 1; min-width: 150px;"><strong>금리:</strong> ${escapeHtml(product.rate)}</div>
                        </div>
                        <a href="/board/finance-policy?category=${encodeURIComponent(product.purpose)}&targetType=${encodeURIComponent(product.targets.includes(target) ? target : 'general')}" style="color: var(--primary-color); font-weight: bold; text-decoration: none; font-size: 0.85rem;">자세히 보기 &rarr;</a>
                        ${product.checkedAt ? `<small style="display:block; margin-top:8px; color:#6c757d;">공식 출처 확인: ${new Date(product.checkedAt).toLocaleDateString('ko-KR')}${product.updateStatus === 'review' ? ' · 변경 검토 중' : ''}</small>` : ''}
                    </div>
                `).join('');
            } else {
                resultBox.innerHTML = `
                    <div style="text-align:center; color:#999; padding:40px;">
                        <i class="fas fa-exclamation-triangle fa-3x mb-3" style="color:#e0e0e0;"></i><br>
                        <strong>조건에 부합하는 대출 상품을 찾지 못했습니다.</strong><br>
                        <span style="font-size:0.9rem;">다른 요건을 선택하여 검색해 주세요.</span>
                    </div>`;
            }
        });
    }

    // 2. 대출 한도 & 금리 계산기 로직
    const calcForm = document.getElementById('calcForm');
    
    function updateAppliedRate() {
        const rateInput = document.getElementById('rate');
        const appliedRateArea = document.getElementById('appliedRateArea');
        const appliedRateSpan = document.getElementById('appliedRate');
        const discountText = document.getElementById('discountText');
        
        if (!rateInput || !appliedRateArea) return;
        
        const baseRate = parseFloat(rateInput.value) || 0;
        let discount = 0;
        document.querySelectorAll('.rate-discount:checked').forEach(cb => {
            discount += parseFloat(cb.value);
        });
        
        if (discount > 0) {
            appliedRateArea.style.visibility = 'visible';
            const finalRate = Math.max(0.1, baseRate - discount);
            appliedRateSpan.textContent = finalRate.toFixed(2) + '%';
            discountText.textContent = discount.toFixed(2) + '%p';
        } else {
            appliedRateArea.style.visibility = 'hidden';
        }
    }

    if (calcForm) {
        // 실시간 금리 우대조건 반영 리스너 등록
        const rateInput = document.getElementById('rate');
        if (rateInput) {
            rateInput.addEventListener('input', updateAppliedRate);
        }
        document.querySelectorAll('.rate-discount').forEach(cb => {
            cb.addEventListener('change', updateAppliedRate);
        });

        // 폼 제출 이벤트 핸들러
        calcForm.addEventListener('submit', async function(e) {
            e.preventDefault();
            const principalInput = parseFloat(document.getElementById('principal').value);
            const baseRate = parseFloat(document.getElementById('rate').value) || 0;
            const term = parseInt(document.getElementById('term').value);
            const repayType = document.getElementById('repayType').value;
            const request = {
                principal: Math.round(principalInput),
                baseRate: baseRate,
                termYears: term,
                repayType: repayType,
                chkSubscription: Boolean(document.getElementById('chkSubscription').checked),
                chkMultiChildren: Boolean(document.getElementById('chkMultiChildren').checked),
                chkNewborn: Boolean(document.getElementById('chkNewborn').checked),
                chkElectronic: Boolean(document.getElementById('chkElectronic').checked)
            };
            let calculation;
            try {
                const response = await fetch('/api/finance/calculate', {
                    method: 'POST',
                    credentials: 'same-origin',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(request)
                });
                const payload = await response.json().catch(function() { return {}; });
                if (!response.ok) throw new Error(payload.message || '계산을 처리하지 못했습니다.');
                calculation = payload;
            } catch (error) {
                alert(error.message);
                return;
            }
            const principal = Number(calculation.totalPrincipal);
            const totalInterest = Number(calculation.totalInterest);
            const totalRepayment = Number(calculation.totalRepayment);
            const firstMonthVal = Number(calculation.firstMonthAmount);
            const schedule = calculation.schedule || [];

            // 결과 화면 업데이트
            document.getElementById('calcResultVal').textContent = firstMonthVal.toLocaleString('ko-KR') + ' 원';
            
            const repayLabel = repayType === 'level-both' ? '원리금균등상환 기준' : (repayType === 'level-principal' ? '원금균등상환 (1회차 기준)' : '원금만기일시상환 기준');
            document.getElementById('repayTypeLabel').textContent = repayLabel;

            document.getElementById('resPrincipal').textContent = Math.round(principal).toLocaleString('ko-KR') + ' 원';
            document.getElementById('resTotalInterest').textContent = Math.round(totalInterest).toLocaleString('ko-KR') + ' 원';
            document.getElementById('resTotalRepayment').textContent = totalRepayment.toLocaleString('ko-KR') + ' 원';

            // 원금 vs 이자 비율 프로그레스 바 시각화
            const principalPercent = Math.round((principal / totalRepayment) * 100);
            const interestPercent = 100 - principalPercent;

            document.getElementById('percentPrincipal').textContent = `원금 ${principalPercent}%`;
            document.getElementById('percentInterest').textContent = `이자 ${interestPercent}%`;
            document.getElementById('barPrincipal').style.width = principalPercent + '%';
            document.getElementById('barPrincipal').setAttribute('aria-valuenow', principalPercent);
            document.getElementById('barInterest').style.width = interestPercent + '%';
            document.getElementById('barInterest').setAttribute('aria-valuenow', interestPercent);

            // 월별 상환 스케줄 렌더링
            const scheduleBody = document.getElementById('scheduleBody');
            scheduleBody.innerHTML = schedule.map(row => `
                <tr>
                    <td class="fw-bold">${row.month}회차</td>
                    <td>${row.principalPaid.toLocaleString('ko-KR')} 원</td>
                    <td>${row.interest.toLocaleString('ko-KR')} 원</td>
                    <td class="fw-bold text-primary">${row.totalPaid.toLocaleString('ko-KR')} 원</td>
                    <td style="color:#6c757d;">${row.balance.toLocaleString('ko-KR')} 원</td>
                </tr>
            `).join('');
            document.getElementById('scheduleArea').style.display = 'block';

            // 토글 버튼 초기화
            const btnToggleSchedule = document.getElementById('btnToggleSchedule');
            btnToggleSchedule.innerHTML = '스케줄 접기 <i class="fas fa-chevron-up ms-1"></i>';
        });
    }

    // 상세 스케줄 토글 접기/펴기 로직
    const btnToggleSchedule = document.getElementById('btnToggleSchedule');
    const scheduleArea = document.getElementById('scheduleArea');
    if (btnToggleSchedule && scheduleArea) {
        btnToggleSchedule.addEventListener('click', function() {
            const isHidden = scheduleArea.style.display === 'none';
            if (isHidden) {
                scheduleArea.style.display = 'block';
                btnToggleSchedule.innerHTML = '스케줄 접기 <i class="fas fa-chevron-up ms-1"></i>';
            } else {
                scheduleArea.style.display = 'none';
                btnToggleSchedule.innerHTML = '스케줄 보기 <i class="fas fa-chevron-down ms-1"></i>';
            }
        });
    }

    // 프린트 PDF 저장 및 공유 링크 복사 로직
    const btnPrint = document.getElementById('btnPrint');
    if (btnPrint) {
        btnPrint.addEventListener('click', function() {
            window.print();
        });
    }

    const btnCopyLink = document.getElementById('btnCopyLink');
    if (btnCopyLink) {
        btnCopyLink.addEventListener('click', function() {
            const p = document.getElementById('principal').value;
            const r = document.getElementById('rate').value;
            const t = document.getElementById('term').value;
            const type = document.getElementById('repayType').value;
            const discounts = [];
            document.querySelectorAll('.rate-discount:checked').forEach(cb => discounts.push(cb.id));

            const url = `${window.location.origin}${window.location.pathname}?p=${p}&r=${r}&t=${t}&type=${type}&discounts=${discounts.join(',')}`;

            navigator.clipboard.writeText(url).then(() => {
                const toastFn = window.showToast || alert;
                toastFn('계산 결과 공유 링크가 클립보드에 복사되었습니다!');
            }).catch(() => {
                alert('링크 복사에 실패했습니다.');
            });
        });
    }

    // 공유 링크 매개변수가 있을 때 자동 로딩 및 계산 실행
    const params = new URLSearchParams(window.location.search);
    if (params.has('p') || params.has('r') || params.has('t')) {
        if (params.has('p')) document.getElementById('principal').value = params.get('p');
        if (params.has('r')) document.getElementById('rate').value = params.get('r');
        if (params.has('t')) document.getElementById('term').value = params.get('t');
        if (params.has('type')) document.getElementById('repayType').value = params.get('type');

        if (params.has('discounts')) {
            const discounts = params.get('discounts').split(',');
            discounts.forEach(id => {
                const cb = document.getElementById(id);
                if (cb) cb.checked = true;
            });
        }

        updateAppliedRate();

        setTimeout(() => {
            if (calcForm) {
                calcForm.dispatchEvent(new Event('submit'));
            }
        }, 100);
    }
});
