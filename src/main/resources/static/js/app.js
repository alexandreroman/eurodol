/*
 * Copyright (c) 2019 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

if (apiKey.length != 0) {
    axios.defaults.headers.common["X-APIKEY"] = apiKey;
}

var app = new Vue({
    data: {
        ready: false,
        amount: 100,
        symbol: "EUR",
        symbols: ["EUR", "USD"],
        showResults: false,
        showError: false,
        amountResult: 1,
        symbolResult: "USD"
    },
    methods: {
        onConvert: function () {
            var url = apiEndpoint + apiEnv + "/api/v1/convert?amount=" + this.amount + "&symbol=" + this.symbol;
            axios.get(url).then(resp => {
                this.amountResult = resp.data.output.amount.toLocaleString(
                    undefined,
                    {
                        minimumFractionDigits: 2,
                        maximumFractionDigits: 2
                    }
                );
                this.symbolResult = resp.data.output.symbol;
            }).catch(error => {
                console.log("Server error: " + error);
                this.showError = true;
            }).finally(e => {
                this.showResults = true;
            });
        },
        onInputUpdated: function () {
            this.showResults = false;
        },
        onGetSourceCode: function () {
            document.location = "https://github.com/alexandreroman/eurodol";
        }
    },
    mounted: function () {
        document.getElementById("app").style.display = "block";
    },
}).$mount("#app");
