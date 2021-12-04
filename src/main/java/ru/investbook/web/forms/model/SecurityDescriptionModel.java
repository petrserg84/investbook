/*
 * InvestBook
 * Copyright (C) 2021  Vitalii Ananev <spacious-team@ya.ru>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ru.investbook.web.forms.model;

import lombok.Data;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@Data
public class SecurityDescriptionModel {

    @Nullable
    private String securityId;
    /**
     * In "name (isin)" or "contract-name" format
     */
    @NotEmpty
    private String security;

    @NotEmpty
    private String sector;

    @NotNull
    private SecurityType securityType;

    public void setSecurity(String securityId, String securityName, SecurityType securityType) {
        this.securityId = securityId;
        this.security = SecurityHelper.getSecurityDescription(securityId, securityName, securityType);
        this.securityType = securityType;
    }

    public String getSecurityDisplayName() {
        return SecurityHelper.getSecurityDisplayName(security, securityType);
    }

    /**
     * Returns ISIN (stock market) or contract name (derivatives and forex market) or null for new security
     */
    public String getSecurityId() {
        if (StringUtils.hasLength(securityId)) {
            return securityId; // если сектор существующей бумаги
        } else if (StringUtils.hasLength(security) && securityType != null) {
            return SecurityHelper.getSecurityId(security, securityType); // получили параметры новой бумаги с формы
        }
        return null; // передаем новую ценную бумагу на форму
    }

    /**
     * Returns security name (stock market) or null (derivatives and forex market)
     */
    public String getSecurityName() {
        return SecurityHelper.getSecurityName(security, securityType);
    }
}
